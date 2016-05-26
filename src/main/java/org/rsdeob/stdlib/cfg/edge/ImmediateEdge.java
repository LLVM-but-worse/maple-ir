package org.rsdeob.stdlib.cfg.edge;

import org.rsdeob.stdlib.collections.graph.FastGraphVertex;

public class ImmediateEdge<N extends FastGraphVertex> extends FlowEdge<N> {
	
	public ImmediateEdge(N src, N dst) {
		super(src, dst);
	}

	@Override
	public String toGraphString() {
		return "Immediate";
	}

	@Override
	public String toString() {
		return String.format("Immediate #%s -> #%s", src.getId(), dst.getId());
	}

	@Override
	public String toInverseString() {
		return String.format("Immediate #%s <- #%s", dst.getId(), src.getId());
	}
	
	@Override
	public ImmediateEdge<N> clone(N src, N dst) {
		return new ImmediateEdge<N>(src, dst);
	}
}