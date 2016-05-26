package org.rsdeob.stdlib.cfg.edge;

import org.objectweb.asm.tree.JumpInsnNode;
import org.rsdeob.stdlib.cfg.BasicBlock;

public class UnconditionalJumpEdge extends JumpEdge {
	
	public UnconditionalJumpEdge(BasicBlock src, BasicBlock dst, JumpInsnNode jump) {
		super(src, dst, jump);
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
	public FlowEdge clone(BasicBlock src, BasicBlock dst) {
		return new UnconditionalJumpEdge(src, dst, jump);
	}
}