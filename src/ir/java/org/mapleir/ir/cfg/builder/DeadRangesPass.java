package org.mapleir.ir.cfg.builder;

import java.util.HashSet;
import java.util.Set;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;

public class DeadRangesPass extends ControlFlowGraphBuilder.BuilderPass {

	public DeadRangesPass(ControlFlowGraphBuilder builder) {
		super(builder);
	}
	
	private boolean canThrow(Statement s) {
		// TODO: refine this analysis.
		int opcode = s.getOpcode();
		return opcode != Opcode.UNCOND_JUMP;
	}

	@Override
	public void run() {
		Set<ExceptionRange<BasicBlock>> rangeWl = new HashSet<>();
		Set<ExceptionRange<BasicBlock>> handlerWl = new HashSet<>();
		Set<BasicBlock> liveHandlers = new HashSet<>();
		
		for(ExceptionRange<BasicBlock> e : builder.graph.getRanges()) {
			boolean canThrow = false;
			
			for(BasicBlock b : e.get()) {
				for(Statement s : b) {
					if(canThrow(s)) {
						canThrow = true;
						break;
					}
				}
			}
			
			// note that if there are no blocks
			// in the range, then canThrow = false here.
			BasicBlock h = e.getHandler();
			if(!canThrow) {
				rangeWl.add(e);
				handlerWl.add(e);
			} else {
				liveHandlers.add(h);
			}
		}
		
		System.out.println("Ranges: ");
		for(ExceptionRange<BasicBlock> e : rangeWl) {
			// if it's a handler, handle it
			// later.
			
			// since these blocks are empty,
			// they should not branch anywhere
			// in the middle of the range to
			// outside.
			
			// this means we can simply remove
			// the blocks in the range and
			// connect the in edges to the out edges
			for(BasicBlock b : e.get()) {
				
			}
		}
	}
}