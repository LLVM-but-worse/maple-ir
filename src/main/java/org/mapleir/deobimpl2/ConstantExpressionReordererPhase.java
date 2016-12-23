package org.mapleir.deobimpl2;

import java.util.List;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.stmt.ConditionalJumpStatement;
import org.mapleir.ir.code.stmt.ConditionalJumpStatement.ComparisonType;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.deob.IPhase;
import org.objectweb.asm.tree.MethodNode;

public class ConstantExpressionReordererPhase implements IPhase, Opcode {

	@Override
	public String getId() {
		return getClass().getSimpleName();
	}

	@Override
	public void accept(IContext cxt, IPhase prev, List<IPhase> completed) {
		for(MethodNode m : cxt.getActiveMethods()) {
			ControlFlowGraph ir = cxt.getIR(m);
			
			transform(ir);
		}
	}
	
	private void transform(ControlFlowGraph ir) {
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
						}
					}
				}
			}
		}
	}
	
	private boolean shouldReorder(Expr r, Expr l) {
		return (l.getOpcode() == CONST_LOAD) && (r.getOpcode() != CONST_LOAD);
	}
}