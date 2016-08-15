package org.mapleir.stdlib.cfg.edge;

import org.mapleir.stdlib.collections.graph.FastGraphVertex;

public class DummyEdge<N extends FastGraphVertex> extends FlowEdge<N>{

	public DummyEdge(N src, N dst) {
		super(DUMMY, src, dst);
	}

	@Override
	public String toGraphString() {
		return "Dummy";
	}

	@Override
	public String toString() {
		return "Dummy";
	}

	@Override
	public String toInverseString() {
		return "Dummy";
	}

	@Override
	public FlowEdge<N> clone(N src, N dst) {
		return new DummyEdge<>(src, dst);
	}
}