package org.rsdeob.stdlib.cfg.edge;

import org.objectweb.asm.tree.JumpInsnNode;
import org.rsdeob.stdlib.cfg.BasicBlock;

public class ConditionalJumpEdge extends JumpEdge {
	
	public ConditionalJumpEdge(BasicBlock src, BasicBlock dst, JumpInsnNode jump) {
		super(src, dst, jump);
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
	public FlowEdge clone(BasicBlock src, BasicBlock dst) {
		return new ConditionalJumpEdge(src, dst, jump);
	}
}