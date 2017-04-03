package org.mapleir.deob.passes.eval;

import org.mapleir.context.IContext;
import org.mapleir.deob.IPass;
import org.mapleir.deob.interproc.IPConstAnalysis;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.edge.FlowEdge;
import org.mapleir.ir.cfg.edge.FlowEdges;
import org.mapleir.ir.cfg.edge.UnconditionalJumpEdge;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.ArithmeticExpr;
import org.mapleir.ir.code.expr.ArithmeticExpr.Operator;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.ConditionalJumpStmt;
import org.mapleir.ir.code.stmt.ConditionalJumpStmt.ComparisonType;
import org.mapleir.ir.code.stmt.NopStmt;
import org.mapleir.ir.code.stmt.UnconditionalJumpStmt;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.util.TypeUtils;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mapleir.deob.passes.eval.ExpressionEvaluator.isValidSet;

public class ConstantExpressionEvaluatorPass implements IPass, Opcode {
	ExpressionEvaluator evaluator;
	
	public ConstantExpressionEvaluatorPass() {
		evaluator = new ExpressionEvaluator();
	}
	
	@Override
	public int accept(IContext cxt, IPass prev, List<IPass> completed) {
		int branchesEvaluated = 0;
		int exprsEvaluated = 0;
		
		IPConstAnalysisVisitor vis = new IPConstAnalysisVisitor(cxt);
		IPConstAnalysis.create(cxt, vis);
		
		for(;;) {
			int prevExprsEval = exprsEvaluated;
			int prevBranchesEval = branchesEvaluated;
			
			for(ClassNode cn : cxt.getApplication().iterate()) {
				for(MethodNode m : cn.methods) {
					
					ControlFlowGraph cfg = cxt.getIRCache().getFor(m);
					LocalsPool pool = cfg.getLocals();
					
					for(BasicBlock b : new HashSet<>(cfg.vertices())) {
						for(int i=0; i < b.size(); i++) {
							Stmt stmt = b.get(i);
							
							if(stmt.getOpcode() == COND_JUMP && simplifyConditionalBranch(vis, cfg, (ConditionalJumpStmt)stmt, i)) {
								 branchesEvaluated++;
							}

							for(CodeUnit e : stmt.enumerateExecutionOrder()) {
								if(e instanceof Expr) {
									CodeUnit par = ((Expr) e).getParent();
									if(par != null) {
										exprsEvaluated += simplifyArithmetic(pool, par, (Expr) e);
									}
								}
							}
						}
					}
				}
			}
			
			if(prevExprsEval == exprsEvaluated && prevBranchesEval == branchesEvaluated) {
				break;
			}
		}
		
		System.out.printf("  evaluated %d constant expressions.%n", exprsEvaluated);
		System.out.printf("  eliminated %d constant branches.%n", branchesEvaluated);
		
		return exprsEvaluated;
	}
	
	private int simplifyArithmetic(LocalsPool pool, CodeUnit par, Expr e) {
		int j = 0;
		
		/* no point evaluating constants */
		if(e.getOpcode() != CONST_LOAD) {
			Expr val = evaluator.eval(pool, e);
			if(val != null) {
				if(!val.equivalent(e)) {
					try {
						overwrite(par, e, val, pool);
					} catch(RuntimeException ex) {
						System.err.println("e: " + e);
						System.err.println("v: " + val);
						throw ex;
					}
					e = val;
					j++;
				}
			}
			
			// TODO: fix this hack for nested exprs
			if(e.getOpcode() == ARITHMETIC) {
				ArithmeticExpr ae = (ArithmeticExpr) e;
				Operator op = ae.getOperator();
				
				Expr e2 = evaluator.simplify(pool, ae);
				
				if(e2 != null) {
					overwrite(par, e, e2, pool);
					j++;
				} else if (op == Operator.MUL) {
					/* (x * c) * k
					 * to
					 * (x * ck)
					 */
					Expr l = ae.getLeft();
					Expr r = ae.getRight();
					
					if(l.getOpcode() == ARITHMETIC) {
						ArithmeticExpr xcExpr = (ArithmeticExpr) l;

						if(xcExpr.getOperator() == Operator.MUL) {
							Expr r2 = xcExpr.getRight();
							Expr c = evaluator.eval(pool, r2);
							Expr k = evaluator.eval(pool, r);
							if(c != null && k != null) {
								ConstantExpr cc = (ConstantExpr) c;
								ConstantExpr ck = (ConstantExpr) k;
								Object v = evaluator.evalMultiplication(c, cc, k, ck);
								
								ArithmeticExpr newAe = new ArithmeticExpr(new ConstantExpr(v, c.getType()), xcExpr.getLeft().copy(), Operator.MUL);
								overwrite(ae.getParent(), ae, newAe, pool);
								j++;
							}
						}
					}
				}
			}
		}
		
		return j;
	}
	
	private void overwrite(CodeUnit parent, Expr from, Expr to, LocalsPool pool) {
		updateDefuse(from, to, pool);
		parent.overwrite(to, parent.indexOf(from));
	}
	
	private void updateDefuse(Expr from, Expr to, LocalsPool pool) {
		// remove uses in from
		for(Expr e : from.enumerateWithSelf()) {
			if (e.getOpcode() == Opcode.LOCAL_LOAD) {
				VersionedLocal l = (VersionedLocal) ((VarExpr) e).getLocal();
				pool.uses.get(l).remove(e);
			}
		}
		
		// add uses in to
		for(Expr e : to.enumerateWithSelf()) {
			if (e.getOpcode() == Opcode.LOCAL_LOAD) {
				VarExpr var = (VarExpr) e;
				pool.uses.get((VersionedLocal) var.getLocal()).add(var);
			}
		}
	}
	
	// todo: move this into the evaluator FFS
	private boolean simplifyConditionalBranch(IPConstAnalysisVisitor vis, ControlFlowGraph cfg, ConditionalJumpStmt cond, int i) {
		Expr l = cond.getLeft();
		Expr r = cond.getRight();
		
		if (!TypeUtils.isPrimitive(l.getType()) || !TypeUtils.isPrimitive(r.getType())) {
			if(l instanceof ConstantExpr && r instanceof ConstantExpr && !TypeUtils.isPrimitive(l.getType()) && !TypeUtils.isPrimitive(r.getType())) {
				return attemptNullarityBranchElimination(cfg, cond.getBlock(), cond, i, (ConstantExpr) l, (ConstantExpr) r);
			}
			return false;
		}
		
		LocalValueResolver resolver;
		
		LocalsPool pool = cfg.getLocals();
		if(vis != null) {
			// FIXME: use
			resolver = new LocalValueResolver.SemiConstantLocalValueResolver(cfg.getMethod(), pool, vis);
		} else {
			resolver = new LocalValueResolver.PooledLocalValueResolver(pool);
		}
		
		Set<ConstantExpr> lSet = evaluator.evalPossibleValues(resolver, l);
		Set<ConstantExpr> rSet = evaluator.evalPossibleValues(resolver, r);
		
		if(isValidSet(lSet) && isValidSet(rSet)) {
			Boolean result = evaluator.evaluateConditional(cond, lSet, rSet);
			if (result != null) {
				eliminateBranch(cfg, cond.getBlock(), cond, i, result);
				return true;
			}
		}
		return false;
	}
	
	// todo: move this into the evaluator and join it with evaluateConditional
	private boolean attemptNullarityBranchElimination(ControlFlowGraph cfg, BasicBlock b, ConditionalJumpStmt cond, int insnIndex, ConstantExpr left, ConstantExpr right) {
		if (left.getConstant() == null && right.getConstant() == null) {
			eliminateBranch(cfg, b, cond, insnIndex, cond.getComparisonType() == ComparisonType.EQ);
			return true;
		}
		if (cond.getComparisonType() == ComparisonType.EQ) {
			if ((left.getConstant() == null) != (right.getConstant() == null)) {
				eliminateBranch(cfg, b, cond, insnIndex, false);
				return true;
			}
		}
		return false;
	}
	
	private void eliminateBranch(ControlFlowGraph cfg, BasicBlock b, ConditionalJumpStmt cond, int insnIndex, boolean val) {
		LocalsPool pool = cfg.getLocals();

		for(FlowEdge<BasicBlock> fe : new HashSet<>(cfg.getEdges(b))) {
			if(fe.getType() == FlowEdges.COND) {
				if(fe.dst != cond.getTrueSuccessor()) {
					throw new IllegalStateException(fe + ", " + cond);
				}
				
				cfg.excisePhiUses(fe);
				cfg.removeEdge(b, fe);
			}
		}
		cfg.exciseStmt(cond);
		if(val) {
			// always true, jump to true successor
			for(FlowEdge<BasicBlock> fe : new HashSet<>(cfg.getEdges(b))) {
				if(fe.getType() == FlowEdges.IMMEDIATE) {
					cfg.excisePhiUses(fe);
					cfg.removeEdge(b, fe);
				}
			}
			UnconditionalJumpStmt newJump = new UnconditionalJumpStmt(cond.getTrueSuccessor());
			b.set(insnIndex, newJump);
			UnconditionalJumpEdge<BasicBlock> uje = new UnconditionalJumpEdge<>(b, cond.getTrueSuccessor());
			cfg.addEdge(b, uje);
		} else {
			// always false, keep immediate (fallthrough) and
			// remove the conditional branch.
			b.set(insnIndex, new NopStmt());
		}
	}
}