package org.mapleir.stdlib.cfg.edge;

import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;

public abstract class FlowEdge<N extends FastGraphVertex> extends FastGraphEdge<N> {
		
	public FlowEdge(N src, N dst) {
		super(src, dst);
	}
	
	public abstract String toGraphString();
	
	@Override
	public abstract String toString();
	
	public abstract String toInverseString();
	
	public abstract FlowEdge<N> clone(N src, N dst);
}