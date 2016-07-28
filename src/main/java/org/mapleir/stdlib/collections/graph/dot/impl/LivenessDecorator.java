package org.mapleir.stdlib.collections.graph.dot.impl;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.ir.locals.Local;
import org.mapleir.stdlib.ir.transform.ssa.SSABlockLivenessAnalyser;
import org.mapleir.stdlib.ir.transform.ssa.SSALivenessAnalyser;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class LivenessDecorator extends BlockCommentDecorator<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> {
	protected ControlFlowGraph graph;
	private SSABlockLivenessAnalyser blockLiveness;
	private SSALivenessAnalyser liveness;
		
	@Override
	public LivenessDecorator setGraph(ControlFlowGraph graph) {
		this.graph = graph;
		if (blockLiveness != null || liveness != null)
			applyComments();
		return this;
	}
	
	public LivenessDecorator setBlockLiveness(SSABlockLivenessAnalyser blockLiveness) {
		this.blockLiveness = blockLiveness;
		if (graph != null)
			applyComments();
		return this;
	}
	
	public LivenessDecorator setLiveness(SSALivenessAnalyser liveness) {
		this.liveness = liveness;
		if (graph != null)
			applyComments();
		return this;
	}
	
	public LivenessDecorator applyComments() {
		clearComments();
		for (BasicBlock n : graph.vertices()) {
			if (blockLiveness != null) {
				addStartComment(n, "IN: " + blockLiveness.in(n).toString());
				addEndComment(n, "OUT: " + blockLiveness.out(n).toString());
			} else if (liveness != null) {
				Set<Local> liveIn = new HashSet<>();
				for (Map.Entry<Local, Boolean> e : liveness.in(n).entrySet())
					if (e.getValue())
						liveIn.add(e.getKey());
				Set<Local> liveOut = new HashSet<>();
				for (Map.Entry<Local, Boolean> e : liveness.out(n).entrySet())
					if (e.getValue())
						liveOut.add(e.getKey());
				addStartComment(n, "IN: " + liveIn.toString());
				addEndComment(n, "OUT: " + liveOut.toString());
			}
		}
		return this;
	}
}
