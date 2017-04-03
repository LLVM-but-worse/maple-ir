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
import org.mapleir.ir.code.stmt.ConditionalJumpStmt;
import org.mapleir.ir.code.stmt.NopStmt;
import org.mapleir.ir.code.stmt.UnconditionalJumpStmt;
import org.mapleir.ir.locals.LocalsPool;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashSet;
import java.util.List;

public class ConstantExpressionEvaluatorPass implements IPass, Opcode {
	private ExpressionEvaluator evaluator;
	private int branchesEvaluated, exprsEvaluated;
	
	public ConstantExpressionEvaluatorPass() {
		evaluator = new ExpressionEvaluator();
	}
	
	@Override
	public int accept(IContext cxt, IPass prev, List<IPass> completed) {
		branchesEvaluated = 0;
		exprsEvaluated = 0;
		
		IPConstAnalysisVisitor vis = new IPConstAnalysisVisitor(cxt);
		IPConstAnalysis.create(cxt, vis);
		
		for(;;) {
			int prevExprsEval = exprsEvaluated;
			int prevBranchesEval = branchesEvaluated;
			
			for(ClassNode cn : cxt.getApplication().iterate()) {
				for(MethodNode m : cn.methods) {
					processMethod(vis, cxt.getIRCache().getFor(m));
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
	
	private void processMethod(IPConstAnalysisVisitor vis, ControlFlowGraph cfg) {
		for(BasicBlock b : new HashSet<>(cfg.vertices())) {
			for(int i=0; i < b.size(); i++) {
				Stmt stmt = b.get(i);
				
				// simplify conditional branches.
				if(stmt.getOpcode() == COND_JUMP) {
					// todo: satisfiability analysis
					ConditionalJumpStmt cond = (ConditionalJumpStmt) stmt;
					Boolean result = evaluator.evaluateConditional(vis, cfg, cond);
					if (result != null) {
						eliminateBranch(cfg, cond.getBlock(), cond, i, result);
						 branchesEvaluated++;
					}
				}

				// evaluate arithmetic.
				for(CodeUnit e : stmt.enumerateExecutionOrder()) {
					if(e instanceof Expr) {
						CodeUnit par = ((Expr) e).getParent();
						if(par != null) {
							exprsEvaluated += simplifyArithmetic(cfg, par, (Expr) e);
						}
					}
				}
			}
		}
	}
	
	private int simplifyArithmetic(ControlFlowGraph cfg, CodeUnit par, Expr e) {
		LocalsPool pool = cfg.getLocals();
		int j = 0;
		
		// no point evaluating constants
		if(e.getOpcode() != CONST_LOAD) {
			Expr val = evaluator.eval(pool, e);
			if(val != null) {
				if(!val.equivalent(e)) {
					try {
						cfg.overwrite(par, e, val);
					} catch(RuntimeException ex) {
						System.err.println("e: " + e);
						System.err.println("v: " + val);
						throw ex;
					}
					e = val;
					j++;
				}
			}
			
			if(e.getOpcode() == ARITHMETIC) {
				ArithmeticExpr ae = (ArithmeticExpr) e;
				Expr e2 = evaluator.simplify(pool, ae); // try evaluation first
				if (e2 == null) { // if that fails try to reassociate
					e2 = reassociate(pool, ae);
				}
				if(e2 != null) {
					cfg.overwrite(par, e, e2);
					j++;
				}
			}
		}
		
		return j;
	}
	
	private ArithmeticExpr reassociate(LocalsPool pool, ArithmeticExpr ae) {
		Operator op = ae.getOperator();
		
		if (op == Operator.MUL) {
			Expr l = ae.getLeft();
			Expr r = ae.getRight();
			Operator operator;
			
			if (l.getOpcode() == ARITHMETIC) {
				ArithmeticExpr xcExpr = (ArithmeticExpr) l;
				
				if (xcExpr.getOperator() == Operator.MUL) {
					Expr r2 = xcExpr.getRight();
					Expr c = evaluator.eval(pool, r2);
					Expr k = evaluator.eval(pool, r);
					if (c != null && k != null) {
						ConstantExpr cc = (ConstantExpr) c;
						ConstantExpr ck = (ConstantExpr) k;
						Object v = evaluator.eval(pool, new ArithmeticExpr(cc, ck, Operator.MUL));
						return new ArithmeticExpr(new ConstantExpr(v, c.getType()), xcExpr.getLeft().copy(), Operator.MUL);
					}
				}
			}
		}
		return null;
	}
	
	private void eliminateBranch(ControlFlowGraph cfg, BasicBlock b, ConditionalJumpStmt cond, int insnIndex, boolean val) {
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