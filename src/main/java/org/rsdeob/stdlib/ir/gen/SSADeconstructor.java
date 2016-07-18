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
	private final ControlFlowGraph cfg;
	
	private final Map<VersionedLocal, BasicLocal> undroppableLocals;
	private final Map<Local, Type> localTypes;
	private int maxLocals;
	
	public SSADeconstructor(CodeBody body, ControlFlowGraph cfg) {
		this.body = body;
		this.cfg = cfg;
		locals = body.getLocals();
		undroppableLocals = new HashMap<>();
		localTypes = new HashMap<>();
		
		init_blocks();
	}
	
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
	
	private void init_blocks() {
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
	
	private void unroll_phis() {
		for (Statement stmt : new HashSet<>(body)) {
			if (stmt instanceof CopyVarStatement) {
				CopyVarStatement copy = (CopyVarStatement) stmt;
				Expression expr = copy.getExpression();
				if (expr instanceof PhiExpression) {
					PhiExpression phi = (PhiExpression) expr;
					unroll(phi, copy.getVariable().getLocal());
					body.remove(copy);
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
			} else {
				throw new UnsupportedOperationException(header.toString() + ", " + header.getClass().getCanonicalName());
			}
		}
	}
	
	private void calculateUndroppables() {
		SSALocalAccess localsAccess = new SSALocalAccess(body);
		for (VersionedLocal local : localsAccess.defs.keySet())
			if (local.getIndex() > maxLocals)
				maxLocals = local.getIndex();
		
		for (Statement stmt : body) {
			for (Statement s : Statement.enumerate(stmt)) {
				VarExpression var = null;
				if (s instanceof VarExpression)
					var = (VarExpression) s;
				else if (s instanceof CopyVarStatement && ((CopyVarStatement) s).isSynthetic())
					var = ((CopyVarStatement) s).getVariable();
				if (var != null) {
					Local local = var.getLocal();
					localTypes.put(local, var.getType());
//					System.out.println("(2.1)type of " + stmt + " = " + var.getType() + ". (local = " + local + ", value = " + localsAccess.defs.get(local).getExpression() + ")");
				}
			}
		}
		
		NullPermeableHashMap<BasicLocal, Set<Type>> simpleTypes = new NullPermeableHashMap<>(new SetCreator<>());
		for (Entry<Local, Type> e : localTypes.entrySet()) {
			Local local = e.getKey();
			BasicLocal basicLocal = locals.get(local.getIndex(), local.isStack());
			simpleTypes.getNonNull(basicLocal).add(TypeUtils.asSimpleType(e.getValue()));
		}
		for (Entry<BasicLocal, Set<Type>> e : simpleTypes.entrySet()) {
			Set<Type> simpleSet = e.getValue();
			System.out.println("(2.2) " + e.getKey() + ": " + simpleSet);
			if (simpleSet.size() > 1)
				processUndroppable(localsAccess, e.getKey(), simpleSet);
		}
		System.out.println("(2.3)Undroppable locals: " + undroppableLocals);
	}
	
	private void processUndroppable(SSALocalAccess localsAccess, BasicLocal local, Set<Type> set) {
		Map<Type, BasicLocal> newLocals = new HashMap<>();
		for (Type simpleType : set) {
			BasicLocal newLocal = locals.get(++maxLocals, false);
			newLocals.put(simpleType, newLocal);
			System.out.println("Reassigning clash type " + simpleType + " to " + newLocal);
		}
		for (VersionedLocal usedLocal : localsAccess.defs.keySet()) {
			if (usedLocal.isVersionOf(local)) {
				// usedLocal is clashing
				Type complexType = localTypes.get(usedLocal);
				BasicLocal reassignLocal = newLocals.get(TypeUtils.asSimpleType(complexType));
				undroppableLocals.put(usedLocal, reassignLocal);
				replaceLocals(versionedLocal -> versionedLocal == usedLocal, versionedLocal -> reassignLocal);
				System.out.println("(2.4) Reassigned clash local " + usedLocal + "(type=" + complexType + ") to " + reassignLocal);
			}
		}
	}
	
	private void drop_subscripts() { // yes my child, it's this easy
		replaceLocals(versionedLocal -> true, versionedLocal -> locals.get(versionedLocal.getIndex(), versionedLocal.isStack()));
	}
	
	public void run() {
		unroll_phis();
		calculateUndroppables();
		drop_subscripts();
	}
}