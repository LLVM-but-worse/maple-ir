package org.mapleir.flowgraph.edges;

import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;

public abstract class FlowEdge<N extends FastGraphVertex> extends FastGraphEdge<N> implements FlowEdges {
	
	private final int type;

	public FlowEdge(int type, N src, N dst) {
		super(src, dst);
		this.type = type;
	}
	
	public int getType() {
		return type;
	}
	
	public abstract String toGraphString();
	
	@Override
	public abstract String toString();
	
	public abstract String toInverseString();
	
	public abstract FlowEdge<N> clone(N src, N dst);

	// compareTo has been moved to FlowGraph#createSet.
}
