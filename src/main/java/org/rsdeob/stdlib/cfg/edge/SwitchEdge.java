package org.rsdeob.stdlib.cfg.edge;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.rsdeob.stdlib.cfg.BasicBlock;

public class SwitchEdge extends FlowEdge {
	
	public final AbstractInsnNode insn;
	public final int value;
	
	public SwitchEdge(BasicBlock src, BasicBlock dst, AbstractInsnNode insn, int value) {
		super(src, dst, new InverseSwitchEdge(dst, src, value));
		this.insn = insn;
		this.value = value;
	}
	
	public SwitchEdge(BasicBlock src, BasicBlock dst, InverseFlowEdge inverse, AbstractInsnNode insn, int value) {
		super(src, dst, inverse);
		this.insn = insn;
		this.value = value;
	}
	
	@Override
	public String toGraphString() {
		return "Case: " + value;
	}
	
	@Override
	public String toString() {
		return String.format("Switch[%d] #%s -> #%s", value, src.getId(), dst.getId());
	}

	@Override
	public SwitchEdge clone(BasicBlock src, BasicBlock dst) {
		return new SwitchEdge(src, dst, insn, value);
	}
}