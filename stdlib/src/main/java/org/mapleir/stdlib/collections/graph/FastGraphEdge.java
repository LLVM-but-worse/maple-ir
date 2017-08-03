package org.mapleir.stdlib.collections.graph;

public class FastGraphEdge<N extends FastGraphVertex> implements Comparable<FastGraphEdge<N>> {

	public final N src;
	public final N dst;
	
	public FastGraphEdge(N src, N dst) {
		this.src = src;
		this.dst = dst;
	}

	@Override
	public int compareTo(FastGraphEdge<N> arg0) {
		return 0;
	}
}
