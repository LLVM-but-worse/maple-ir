package org.mapleir.flowgraph.edges;

import org.mapleir.stdlib.collections.graph.FastGraphEdgeImpl;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;

public abstract class AbstractFlowEdge<N extends FastGraphVertex> extends FastGraphEdgeImpl<N> implements FlowEdge<N>, FlowEdges {
	protected final int type;

	public AbstractFlowEdge(int type, N src, N dst) {
		super(src, dst);
		this.type = type;
	}

	public int getType() {
		return type;
	}
}
