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
import org.mapleir.ir.code.expr.NegationExpr;
import org.mapleir.ir.code.stmt.ConditionalJumpStmt;
import org.mapleir.ir.code.stmt.NopStmt;
import org.mapleir.ir.code.stmt.UnconditionalJumpStmt;
import org.mapleir.ir.locals.LocalsPool;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashSet;
import java.util.List;

import static org.mapleir.ir.code.expr.ArithmeticExpr.Operator.*;

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
				Expr e2 = null;
				if (ae.getOperator() == MUL) {
					e2 = evaluator.simplifyMultiplication(pool, ae);
				}
				if (e2 == null && ae.getLeft().getOpcode() == ARITHMETIC) {
					e2 = reassociate(pool, ae);
				}

				if(e2 != null) {
					System.out.println(e + " -> " + e2);
					cfg.overwrite(par, e, e2);
					j++;
				}
			}
		}
		
		return j;
	}
	
	private ArithmeticExpr reassociate(LocalsPool pool, ArithmeticExpr ae) {
		ArithmeticExpr leftAe = (ArithmeticExpr) ae.getLeft();
		Operator operatorA = leftAe.getOperator();
		Operator operatorB = ae.getOperator();
		
		Expr r1 = evaluator.eval(pool, leftAe.getRight());
		Expr r2 = evaluator.eval(pool, ae.getRight());
		if (r1 != null && r2 != null) {
			ConstantExpr cr1 = (ConstantExpr) r1;
			ConstantExpr cr2 = (ConstantExpr) r2;
			
			int sign = 0;
			if ((operatorA == MUL && operatorB == MUL)) {
				sign = 1;
			} else if (operatorA == ADD && (operatorB == ADD || operatorB == SUB)) {
				sign = 1; // what about overflow?? integers mod 2^32 forms a group over addition...should be ok?
			} else if (operatorA == SUB && (operatorB == ADD || operatorB == SUB)) {
				sign = -1;
			}
			if (sign != 0) {
				Expr cr1r2 = evaluator.eval(pool, new ArithmeticExpr(cr1, sign > 0 ? cr2 : new NegationExpr(cr2), operatorB));
				Object associated = ((ConstantExpr) cr1r2).getConstant();
				return new ArithmeticExpr(new ConstantExpr(associated, r1.getType()), leftAe.getLeft().copy(), operatorA);
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