package org.rsdeob.stdlib.cfg.edge;

import org.objectweb.asm.tree.JumpInsnNode;
import org.rsdeob.stdlib.cfg.BasicBlock;

public class ConditionalJumpEdge extends JumpEdge {
	public ConditionalJumpEdge(BasicBlock src, BasicBlock dst, JumpInsnNode jump) {
		super(src, dst, jump, new InverseJumpEdge(dst, src, jump) {
			@Override
			public String toString() {
				return "Conditional" + super.toString();
			}
		});
	}
	
	@Override
	public String toString() {
		return "Conditional" + super.toString();
	}
	
	@Override
	public FlowEdge clone(BasicBlock src, BasicBlock dst) {
		return new ConditionalJumpEdge(src, dst, jump);
	}
}