package org.rsdeob.stdlib.cfg.edge;

import org.rsdeob.stdlib.collections.graph.FastGraphVertex;

public class ConditionalJumpEdge<N extends FastGraphVertex> extends JumpEdge<N> {
	
	public ConditionalJumpEdge(N src, N dst, int opcode) {
		super(src, dst, opcode);
	}
	
	@Override
	public String toString() {
		return "Conditional" + super.toString();
	}
	
	@Override
	public String toInverseString() {
		return "Conditional" + super.toInverseString();
	}
	
	@Override
	public ConditionalJumpEdge<N> clone(N src, N dst) {
		return new ConditionalJumpEdge<N>(src, dst, opcode);
	}
}