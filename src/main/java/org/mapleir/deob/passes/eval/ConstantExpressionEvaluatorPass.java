package org.mapleir.deob.passes.eval;

import org.mapleir.context.IContext;
import org.mapleir.deob.IPass;
import org.mapleir.deob.interproc.IPAnalysis;
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
import org.mapleir.ir.code.stmt.ConditionalJumpStmt;
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
		IPAnalysis.create(cxt, vis);
		
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
				for(CodeUnit cu : stmt.enumerateExecutionOrder()) {
					if(cu instanceof Expr) {
						Expr e = (Expr) cu;
						CodeUnit par = e.getParent();
						if(par != null) {
							Expr val = simplifyArithmetic(cfg.getLocals(), e);
							if (val != null) {
								exprsEvaluated++;
								cfg.overwrite(par, e, val);
								System.out.println("[ConstEval] " + e + " -> " + val);
							}
						}
					}
				}
			}
		}
	}
	
	private Expr simplifyArithmetic(LocalsPool pool, Expr e) {
		// no point evaluating constants
		if (e.getOpcode() == CONST_LOAD) {
			return null;
		}
		
		// try direct evaluation
		Expr val = evaluator.eval(pool, e);
		if(val != null && !val.equivalent(e)) {
			return val;
		}
		
		// try to simplify arithmetic
		if(e.getOpcode() == ARITHMETIC) {
			Expr e2 = evaluator.simplifyArithmetic(pool, (ArithmeticExpr) e);
			if (e2 != null) {
				return e2;
			}
		}
		
		return null;
	}
	
	private void eliminateBranch(ControlFlowGraph cfg, BasicBlock b, ConditionalJumpStmt cond, int insnIndex, boolean val) {
		if(val) { // always true, jump to true successor
			// remove immediate edge (it will never be taken)
			for(FlowEdge<BasicBlock> fe : new HashSet<>(cfg.getEdges(b))) {
				if(fe.getType() == FlowEdges.IMMEDIATE) {
					cfg.exciseEdge(fe);
				}
			}
			
			// create new jump and update cfg
			UnconditionalJumpStmt newJump = new UnconditionalJumpStmt(cond.getTrueSuccessor());
			b.set(insnIndex, newJump);
			UnconditionalJumpEdge<BasicBlock> uje = new UnconditionalJumpEdge<>(b, cond.getTrueSuccessor());
			cfg.addEdge(b, uje);
		} else { // always false, keep immediate (fallthrough) and remove the conditional branch.
			// remove statement amd uses in d/u map
			cfg.exciseStmt(cond);
			
			// remove conditional edge
			for(FlowEdge<BasicBlock> fe : new HashSet<>(cfg.getEdges(b))) {
				if(fe.getType() == FlowEdges.COND) {
					if(fe.dst != cond.getTrueSuccessor()) {
						throw new IllegalStateException(fe + ", " + cond);
					}
					cfg.exciseEdge(fe);
				}
			}
		}
	}
}