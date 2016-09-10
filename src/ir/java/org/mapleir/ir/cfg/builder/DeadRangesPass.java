package org.mapleir.ir.cfg.builder;

import java.util.HashSet;
import java.util.Set;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;

public class DeadRangesPass extends ControlFlowGraphBuilder.BuilderPass {

	public DeadRangesPass(ControlFlowGraphBuilder builder) {
		super(builder);
	}

	@Override
	public void run() {
		Set<BasicBlock> rangeWl = new HashSet<>();
		Set<BasicBlock> handlerWl = new HashSet<>();
		Set<BasicBlock> liveHandlers = new HashSet<>();
		
		for(ExceptionRange<BasicBlock> e : builder.graph.getRanges()) {
			boolean canThrow = false;
			
			for(BasicBlock b : e.get()) {
				if(b.size() >= 0) {
					canThrow = true;
					break;
				}
			}
			
			// note that if there are no blocks
			// in the range, then canThrow = false here.
			
			BasicBlock h = e.getHandler();
			if(!canThrow) {
				rangeWl.addAll(e.get());
				handlerWl.add(h);
			} else {
				liveHandlers.add(h);
			}
		}
		
		for(BasicBlock b : rangeWl) {
			// if it's a handler, handle it
			// later
			if(!liveHandlers.contains(b)) {
				builder.graph.excavate(b);
			}
		}
	}
}