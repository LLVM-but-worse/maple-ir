package org.rsdeob.stdlib.cfg.edge;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.rsdeob.stdlib.collections.graph.FastGraphVertex;

public class DefaultSwitchEdge<N extends FastGraphVertex> extends SwitchEdge<N> {
	
	public DefaultSwitchEdge(N src, N dst, AbstractInsnNode insn) {
		super(src, dst, insn, 0);
	}
	
	@Override
	public String toString() {
		return "Default " + super.toString();	
	}
	
	@Override
	public String toInverseString() {
		return "Default " + super.toInverseString();
	}

	@Override
	public DefaultSwitchEdge<N> clone(N src, N dst) {
		return new DefaultSwitchEdge<N>(src, dst, insn);
	}
}