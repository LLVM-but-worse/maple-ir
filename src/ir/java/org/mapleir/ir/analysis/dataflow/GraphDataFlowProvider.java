package org.mapleir.ir.analysis.dataflow;

import java.util.HashSet;
import java.util.Set;

import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;
import org.mapleir.stdlib.collections.graph.flow.FlowGraph;

public class GraphDataFlowProvider<N extends FastGraphVertex, E extends FlowEdge<N>> implements DataFlowProvider<N, E> {

	private final FlowGraph<N, E> graph;
	
	public GraphDataFlowProvider(FlowGraph<N, E> graph) {
		this.graph = graph;
	}

	@Override
	public Set<E> getSuccessors(N n) {
		return new HashSet<>(graph.getEdges(n));
	}

	@Override
	public Set<E> getPredecessors(N n) {
		return new HashSet<>(graph.getReverseEdges(n));
	}

	@Override
	public Set<N> getNodes() {
		return new HashSet<>(graph.vertices());
	}

	@Override
	public Set<N> getHandlers() {
		Set<N> set = new HashSet<>();
		for(ExceptionRange<N> er : graph.getRanges()) {
			set.add(er.getHandler());
		}
		return set;
	}

	@Override
	public Set<N> getHeads() {
		return new HashSet<>(graph.getEntries());
	}

	@Override
	public Set<N> getTails() {
		Set<N> set = new HashSet<>();
		for(N n : graph.vertices()) {
			if(graph.getEdges(n).size() == 0) {
				set.add(n);
			}
		}
		return set;
	}
}