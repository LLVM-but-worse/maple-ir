package org.rsdeob.stdlib.ir.gen;

import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.util.TypeUtils;
import org.rsdeob.stdlib.collections.NullPermeableHashMap;
import org.rsdeob.stdlib.collections.SetCreator;
import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.expr.Expression;
import org.rsdeob.stdlib.ir.expr.PhiExpression;
import org.rsdeob.stdlib.ir.expr.VarExpression;
import org.rsdeob.stdlib.ir.header.BlockHeaderStatement;
import org.rsdeob.stdlib.ir.header.HeaderStatement;
import org.rsdeob.stdlib.ir.locals.BasicLocal;
import org.rsdeob.stdlib.ir.locals.Local;
import org.rsdeob.stdlib.ir.locals.LocalsHandler;
import org.rsdeob.stdlib.ir.locals.VersionedLocal;
import org.rsdeob.stdlib.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.transform.ssa.SSALocalAccess;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;

public class SSADeconstructor {
	
	private final LocalsHandler locals;
	private final CodeBody body;
	private final SSALocalAccess localsAccess;
	private final ControlFlowGraph cfg;
	
	private final Map<VersionedLocal, BasicLocal> undroppableLocals;
	private final Map<VersionedLocal, Type> localTypes;
	private final NullPermeableHashMap<VersionedLocal, Set<VarExpression>> uses;
	private int maxLocals;
	
	public SSADeconstructor(CodeBody body, ControlFlowGraph cfg) {
		this.body = body;
		this.cfg = cfg;
		localsAccess = new SSALocalAccess(body);
		
		locals = body.getLocals();
		undroppableLocals = new HashMap<>();
		localTypes = new HashMap<>();
		uses = new NullPermeableHashMap<>(new SetCreator<>());
		
		initBlocks();
	}
	
	private void initBlocks() {
		for (BasicBlock b : cfg.vertices()) {
			b.getStatements().clear();
		}
		
		BasicBlock currentHeader = null;
		for (Statement stmt : body) {
			if (stmt instanceof HeaderStatement) {
				currentHeader = cfg.getBlock(((HeaderStatement) stmt).getHeaderId());
			} else {
				if (currentHeader == null) {
					throw new IllegalStateException();
				} else if (!(stmt instanceof PhiExpression)) {
					currentHeader.getStatements().add(stmt);
				}
			}
		}
	}
	
	// Processing code
	public void run() {
		unrollPhis();
		typeVars();
		dropSubscripts();
	}
	
	// Phi removal
	private void unrollPhis() {
		for (Statement stmt : new HashSet<>(body)) {
			if (stmt instanceof CopyVarStatement) {
				CopyVarStatement copy = (CopyVarStatement) stmt;
				Expression expr = copy.getExpression();
				if (expr instanceof PhiExpression) {
					Local l = copy.getVariable().getLocal();
					PhiExpression phi = (PhiExpression) expr;
					unroll(phi, l);
					body.remove(copy);
					localsAccess.defs.remove(l);
					localsAccess.useCount.remove(l);
				}
			}
		}
	}
	
	private void unroll(PhiExpression phi, Local l) {
		for (Entry<HeaderStatement, Expression> e : phi.getLocals().entrySet()) {
			Expression expr = e.getValue();
			if (expr instanceof VarExpression) {
				Local l2 = ((VarExpression) expr).getLocal();
				if (l2.getIndex() == l.getIndex() && l2.isStack() == l.isStack()) {
					continue;
				}
			}
			HeaderStatement header = e.getKey();
			if (header instanceof BlockHeaderStatement) {
				BlockHeaderStatement bh = (BlockHeaderStatement) header;
				BasicBlock block = bh.getBlock();
				List<Statement> stmts = block.getStatements();
				int index = -1;
				if (stmts.isEmpty()) {
					index = body.indexOf(bh) + 1;
				} else {
					Statement last = stmts.get(stmts.size() - 1);
					index = body.indexOf(last);
					if (!last.canChangeFlow()) {
						index += 1;
					}
				}
				
				CopyVarStatement copy = new CopyVarStatement(new VarExpression(l, phi.getType()), expr);
				body.add(index, copy);
				stmts.add(copy);
				localsAccess.defs.put((VersionedLocal) l, copy);
				// FIXME: not a real fixme, just note that useCount in localaccess isn't synced.
			} else {
				throw new UnsupportedOperationException(header.toString() + ", " + header.getClass().getCanonicalName());
			}
		}
	}
	
	// Variable typing
	private void typeVars() {
		computeMaxLocals();
		mapTypes();
		unweaveUndroppables();
	}
	
	void computeMaxLocals() {
		for (VersionedLocal local : localsAccess.defs.keySet())
			maxLocals = Math.max(maxLocals, local.getIndex());
	}
	
	void mapTypes() {
		for (Statement stmt : body) {
			for (Statement child : Statement.enumerate(stmt)) {
				if (child instanceof VarExpression) {
					mapType((VarExpression) child);
				} else if (child instanceof CopyVarStatement && ((CopyVarStatement) child).isSynthetic()) {
					mapType(((CopyVarStatement) child).getVariable());
				}
			}
		}
	}
	
	void mapType(VarExpression var) {
		Local local = var.getLocal();
		if(!(local instanceof VersionedLocal)) {
			throw new IllegalStateException(local + " " + var);
		}
		VersionedLocal versionedLocal = (VersionedLocal) local;
		uses.getNonNull(versionedLocal).add(var);
		localTypes.put(versionedLocal, var.getType());
	}
	
	private void unweaveUndroppables() {
		NullPermeableHashMap<BasicLocal, Set<Type>> simpleTypes = new NullPermeableHashMap<>(new SetCreator<>());
		
		for (Entry<VersionedLocal, Type> e : localTypes.entrySet()) {
			Local versioned = e.getKey();
			BasicLocal unversioned = locals.get(versioned.getIndex(), versioned.isStack());
			simpleTypes.getNonNull(unversioned).add(TypeUtils.asSimpleType(e.getValue()));
		}
		
		for (Entry<BasicLocal, Set<Type>> e : simpleTypes.entrySet()) {
			BasicLocal local = e.getKey();
			Set<Type> types = e.getValue();
			System.out.println("(2.2) " + local + ": " + types);
			if (types.size() > 1) {
				unweaveLocal(local, types);
			}
		}
		System.out.println("(2.3)Undroppable locals: " + undroppableLocals);
	}
	
	private void unweaveLocal(BasicLocal local, Set<Type> set) {
		System.out.println("Need to unweave " + local);
		
		// wind up spindle
		Map<Type, BasicLocal> spindle = new HashMap<>();
		for (Type simpleType : set) {
			BasicLocal newLocal = locals.get(++maxLocals, local.isStack());
			spindle.put(simpleType, newLocal);
			System.out.println("Reassigning clash type " + simpleType + " to " + newLocal);
		}
		
		// unwind spindle
		for (VersionedLocal usedLocal : localsAccess.defs.keySet()) {
			System.out.println(usedLocal + " " + usedLocal.isVersionOf(local));
			if (usedLocal.isVersionOf(local)) {
				// usedLocal is clashing
				Type complexType = localTypes.get(usedLocal);
				if (complexType == null) {
					throw new IllegalStateException(usedLocal.toString());
				}
				BasicLocal reassignLocal = spindle.get(TypeUtils.asSimpleType(complexType));
				undroppableLocals.put(usedLocal, reassignLocal);
				replaceLocals(versionedLocal -> versionedLocal == usedLocal, versionedLocal -> reassignLocal);
				System.out.println("(2.4) Reassigned clash local " + usedLocal + "(type=" + complexType + ") to " + reassignLocal);
			}
		}
	}
	
	// Subscript removal
	private void dropSubscripts() { // yes my child, it's this easy
		replaceLocals(versionedLocal -> true, versionedLocal -> locals.get(versionedLocal.getIndex(), versionedLocal.isStack()));
	}
	
	// End of processing code
	
	private void replaceLocals(Predicate<VersionedLocal> filter, Function<VersionedLocal, BasicLocal> replaceFn) {
		for (Statement stmt : body) {
			for (Statement s : Statement.enumerate(stmt)) {
				VarExpression var = null;
				if (s instanceof VarExpression) {
					var = (VarExpression) s;
				} else if (s instanceof CopyVarStatement) {
					CopyVarStatement copy = (CopyVarStatement) s;
					var = copy.getVariable();
				} else if (s instanceof PhiExpression) {
					throw new IllegalStateException(s.toString());
				}
				if (var != null && var.getLocal() instanceof VersionedLocal) {
					VersionedLocal versionedLocal = (VersionedLocal) var.getLocal();
					if (filter.test(versionedLocal))
						var.setLocal(replaceFn.apply(versionedLocal));
				}
			}
		}
	}
}