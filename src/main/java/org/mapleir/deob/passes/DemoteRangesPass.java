package org.mapleir.deob.passes;

import java.util.List;

import org.mapleir.context.IContext;
import org.mapleir.deob.IPass;
import org.mapleir.deob.intraproc.ExceptionAnalysis;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Stmt;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class DemoteRangesPass implements IPass {
	
	@Override
	public int accept(IContext cxt, IPass prev, List<IPass> completed) {
		for(ClassNode cn : cxt.getApplication().iterate()) {
			for(MethodNode m : cn.methods) {
				ControlFlowGraph cfg = cxt.getIRCache().getFor(m);
				if(cfg.getRanges().size() > 0) {
					process(cfg, cxt.getExceptionAnalysis(cfg));
				}
			}
		}
		return 0;
	}

	private void process(ControlFlowGraph cfg, ExceptionAnalysis analysis) {
		for(ExceptionRange<BasicBlock> er : cfg.getRanges()) {
			/* go through the blocks in code order and
			 * try to demote them one at a time. if we
			 * can't demote the current block, stop
			 * trying for this range (breaking up the
			 * range causes more problems than it solves). */
			for(BasicBlock b : er.get()) {
				for(Stmt stmt : b) {
					
				}
			}
		}
	}
}