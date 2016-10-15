package org.mapleir.ir.cfg.edge;

import org.mapleir.stdlib.collections.graph.FastGraphVertex;

public class UnconditionalJumpEdge<N extends FastGraphVertex> extends JumpEdge<N> {
	
	public UnconditionalJumpEdge(N src, N dst, int opcode) {
		super(UNCOND, src, dst, opcode);
	}

	@Override
	public String toString() {
		return "Unconditional" + super.toString();
	}
	
	@Override
	public String toInverseString() {
		return "Unconditional" + super.toInverseString();
	}
	
	@Override
	public UnconditionalJumpEdge<N> clone(N src, N dst) {
		return new UnconditionalJumpEdge<>(src, dst, opcode);
	}
}