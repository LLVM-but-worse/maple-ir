package org.mapleir.flowgraph.edges;

import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;

public interface FlowEdge<N extends FastGraphVertex> extends FastGraphEdge<N>, FlowEdges {
	
	int getType();
	
	String toGraphString();
	
	@Override
	String toString();
	
	String toInverseString();
	
	FlowEdge<N> clone(N src, N dst);

	@Override
	default int compareTo(FastGraphEdge<N> o) {
		if (getType() == FlowEdges.DUMMY) {
			return 1;
		} else if (((FlowEdge<N>)o).getType() == FlowEdges.DUMMY) {
			return -1;
		}
		return FastGraphEdge.super.compareTo(o);
	}
}
