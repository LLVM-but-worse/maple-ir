package org.rsdeob.stdlib.collections.graph;

public class FastGraphEdge<N> {

	public final N src;
	public final N dst;
	
	public FastGraphEdge(N src, N dst) {
		this.src = src;
		this.dst = dst;
	}
}