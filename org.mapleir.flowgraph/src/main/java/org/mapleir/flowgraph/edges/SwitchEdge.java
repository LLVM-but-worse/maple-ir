package org.mapleir.flowgraph.edges;

import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.objectweb.asm.tree.AbstractInsnNode;

public class SwitchEdge<N extends FastGraphVertex> extends AbstractFlowEdge<N> {
	
	public final AbstractInsnNode insn;
	public final int value;
	
	public SwitchEdge(N src, N dst, AbstractInsnNode insn, int value) {
		super(SWITCH, src, dst);
		this.insn = insn;
		this.value = value;
	}
	
	@Override
	public String toGraphString() {
		return "Case: " + value;
	}
	
	@Override
	public String toString() {
		return String.format("Switch[%d] #%s -> #%s", value, src.getDisplayName(), dst.getDisplayName());
	}

	@Override
	public String toInverseString() {
		return String.format("Switch[%d] #%s <- #%s", value, dst.getDisplayName(), src.getDisplayName());
	}

	@Override
	public SwitchEdge<N> clone(N src, N dst) {
		return new SwitchEdge<>(src, dst, insn, value);
	}
}
