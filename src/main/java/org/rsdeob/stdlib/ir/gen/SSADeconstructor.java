package org.rsdeob.stdlib.ir.gen;

import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.util.TypeUtils;
import org.rsdeob.stdlib.collections.SetMultimap;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static org.rsdeob.stdlib.ir.transform.ssa.SSAUtil.replaceLocals;
import static org.rsdeob.stdlib.ir.transform.ssa.SSAUtil.visitAll;
import static org.rsdeob.stdlib.ir.transform.ssa.SSAUtil.vl;

public class SSADeconstructor {
	
	private final LocalsHandler locals;
	private final CodeBody body;
	private SSALocalAccess localsAccess;
	private final ControlFlowGraph cfg;
	
	private final Map<VersionedLocal, BasicLocal> undroppableLocals;
	private final Map<VersionedLocal, Type> localTypes;
	private final SetMultimap<VersionedLocal, VarExpression> uses; // umm...this is updated but never queried. what
	private int maxLocals;
	
	public SSADeconstructor(CodeBody body, ControlFlowGraph cfg) {
		this.body = body;
		this.cfg = cfg;
		localsAccess = new SSALocalAccess(body);
		
		locals = body.getLocals();
		undroppableLocals = new HashMap<>();
		localTypes = new HashMap<>();
		uses = new SetMultimap<>();
		
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
					phiSanityCheck(phi);
					unroll(phi, l);
					body.remove(copy);
					localsAccess.defs.remove(vl(l));
					localsAccess.useCount.remove(vl(l));
				}
			}
		}
	}
	
	private void phiSanityCheck(PhiExpression phi) {
		// Sanity check
		BasicLocal unversionedLocal = null;
		for (Expression phiLocal : phi.getLocals().values()) {
			for (Statement child : phiLocal.getUsedVars()) {
				VarExpression childVar = (VarExpression) child;
				if (!(childVar.getLocal() instanceof VersionedLocal)) {
					phi.debugPrint();
					throw new IllegalArgumentException("Phi has invalid non-versioned local " + phiLocal);
				} else {
					VersionedLocal versionedLocal = (VersionedLocal) childVar.getLocal();
					if (unversionedLocal == null)
						unversionedLocal = locals.unversion(versionedLocal);
					if (!versionedLocal.isVersionOf(unversionedLocal)) {
						phi.debugPrint();
						throw new IllegalArgumentException("Mismatched base locals " + versionedLocal + " " + unversionedLocal + " (we need to implement register allocation)");
					}
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
		localsAccess = new SSALocalAccess(body);
		computeMaxLocals();
		mapTypes();
		unweaveUndroppables();
		localsAccess = null;
	}
	
	private void computeMaxLocals() {
		for (VersionedLocal local : localsAccess.defs.keySet())
			maxLocals = Math.max(maxLocals, local.getIndex());
	}
	
	private void mapTypes() {
		for (Statement var : visitAll(body, child -> child instanceof VarExpression))
			mapType((VarExpression) var);
		for (Statement cvs : visitAll(body, child -> child instanceof CopyVarStatement && ((CopyVarStatement) child).isSynthetic()))
			mapType(((CopyVarStatement) cvs).getVariable());
	}
	
	private void mapType(VarExpression var) {
		Local local = var.getLocal();
		if(!(local instanceof VersionedLocal)) {
			throw new IllegalStateException(local + " " + var);
		}
		VersionedLocal versionedLocal = (VersionedLocal) local;
		uses.put(versionedLocal, var);
		localTypes.put(versionedLocal, var.getType());
	}
	
	private void unweaveUndroppables() {
		SetMultimap<BasicLocal, Type> simpleTypes = new SetMultimap<>();
		
		for (Entry<VersionedLocal, Type> e : localTypes.entrySet()) {
			VersionedLocal versioned = vl(e.getKey());
			BasicLocal unversioned = locals.unversion(versioned);
			simpleTypes.put(unversioned, TypeUtils.asSimpleType(e.getValue()));
		}
		
		for (Entry<BasicLocal, Set<Type>> e : simpleTypes.asMap().entrySet()) {
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
				BasicLocal reassignLocal = spindle.get(TypeUtils.asSimpleType(complexType));
				undroppableLocals.put(usedLocal, reassignLocal);
				replaceLocals(body, versionedLocal -> versionedLocal == usedLocal, versionedLocal -> reassignLocal);
				System.out.println("(2.4) Reassigned clash local " + usedLocal + "(type=" + complexType + ") to " + reassignLocal);
			}
		}
	}
	
	// Subscript removal
	private void dropSubscripts() { // yes my child, it's this easy
		replaceLocals(body, versionedLocal -> true, locals::unversion);
	}
}