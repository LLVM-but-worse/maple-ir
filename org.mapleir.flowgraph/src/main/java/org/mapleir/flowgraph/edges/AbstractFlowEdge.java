package org.mapleir.flowgraph.edges;

import org.mapleir.stdlib.collections.graph.FastGraphEdgeImpl;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;

public abstract class AbstractFlowEdge<N extends FastGraphVertex> extends FastGraphEdgeImpl<N> implements FlowEdge<N>, FlowEdges {
	protected final int type;

	public AbstractFlowEdge(int type, N src, N dst) {
		super(src, dst);
		this.type = type;
	}

	@Override
	public int getType() {
		return type;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + type;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		AbstractFlowEdge<?> other = (AbstractFlowEdge<?>) obj;
		if (type != other.type)
			return false;
		return true;
	}
}
