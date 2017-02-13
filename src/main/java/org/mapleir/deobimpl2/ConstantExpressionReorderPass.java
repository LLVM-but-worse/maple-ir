package org.mapleir.deobimpl2;

import java.util.List;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.ArithmeticExpr;
import org.mapleir.ir.code.expr.ArithmeticExpr.Operator;
import org.mapleir.ir.code.stmt.ConditionalJumpStatement;
import org.mapleir.ir.code.stmt.ConditionalJumpStatement.ComparisonType;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.deob.ICompilerPass;
import org.objectweb.asm.tree.MethodNode;

public class ConstantExpressionReorderPass implements ICompilerPass, Opcode {

	@Override
	public String getId() {
		return "CESwap";
	}
	
	@Override
	public void accept(IContext cxt, ICompilerPass prev, List<ICompilerPass> completed) {
		int i = 0;
		for(MethodNode m : cxt.getActiveMethods()) {
			ControlFlowGraph ir = cxt.getIR(m);
			i += transform(ir);
		}
		System.out.println("  swapped " + i + " constant expression orders.");
	}
	
	private int transform(ControlFlowGraph ir) {
		int i = 0;
		
		for(BasicBlock b : ir.vertices()) {
			for(Stmt stmt : b) {
				if(stmt.getOpcode() == COND_JUMP) {
					ConditionalJumpStatement cjs = (ConditionalJumpStatement) stmt;
					Expr r = cjs.getRight();
					Expr l = cjs.getLeft();
					
					ComparisonType type = cjs.getComparisonType();
					if(type == ComparisonType.EQ || type == ComparisonType.NE) {
						if(shouldReorder(r, l)) {
							cjs.setRight(null);
							cjs.setLeft(null);
							cjs.setLeft(r);
							cjs.setRight(l);
							i++;
						}
					}
				}
				
				for(Expr e : stmt.enumerateOnlyChildren()) {
					if(e.getOpcode() == ARITHMETIC) {
						ArithmeticExpr arith = (ArithmeticExpr) e;
						Expr r = arith.getRight();
						Expr l = arith.getLeft();
						
						Operator op = arith.getOperator();
						if(!op.doesOrderMatter()) {
							if(shouldReorder(r, l)) {
								arith.setRight(null);
								arith.setLeft(null);
								arith.setLeft(r);
								arith.setRight(l);
								i++;
							}
						}
					}
				}
			}
		}
		
		return i;
	}
	
	private boolean shouldReorder(Expr r, Expr l) {
		return (l.getOpcode() == CONST_LOAD) && (r.getOpcode() != CONST_LOAD);
	}
}