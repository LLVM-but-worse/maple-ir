package org.rsdeob.stdlib.cfg.edge;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.rsdeob.stdlib.cfg.BasicBlock;

public class DefaultSwitchEdge extends SwitchEdge {
	
	public DefaultSwitchEdge(BasicBlock src, BasicBlock dst, AbstractInsnNode insn) {
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
	public SwitchEdge clone(BasicBlock src, BasicBlock dst) {
		return new DefaultSwitchEdge(src, dst, insn);
	}
}