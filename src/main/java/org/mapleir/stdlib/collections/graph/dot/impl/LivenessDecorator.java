package org.mapleir.stdlib.collections.graph.dot.impl;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.ir.transform.ssa.SSABlockLivenessAnalyser;

public class LivenessDecorator extends BlockCommentDecorator<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> {
	protected ControlFlowGraph graph;
	private SSABlockLivenessAnalyser liveness;
		
	@Override
	public LivenessDecorator setGraph(ControlFlowGraph graph) {
		this.graph = graph;
		if (liveness != null)
			applyComments();
		return this;
	}
	
	public LivenessDecorator setLiveness(SSABlockLivenessAnalyser liveness) {
		this.liveness = liveness;
		if (graph != null)
			applyComments();
		return this;
	}
	
	// applyComments for SSABlockLivenessAnalyser
	public LivenessDecorator applyComments() {
		clearComments();
		for (BasicBlock n : graph.vertices()) {
			addStartComment(n, "IN: " + liveness.in(n).toString());
			addEndComment(n, "OUT: " + liveness.out(n).toString());
		}
		return this;
	}
	
	// applyComments for SSALivenessAnalyser
	/*
	public LivenessDecorator applyComments() {
		clearComments();
		for (BasicBlock n : graph.vertices()) {
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
		return this;
	}
	 */
}
