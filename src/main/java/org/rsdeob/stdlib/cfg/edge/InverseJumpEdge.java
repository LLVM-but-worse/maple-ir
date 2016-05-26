package org.rsdeob.stdlib.cfg.edge;

import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.util.Printer;
import org.rsdeob.stdlib.cfg.BasicBlock;

public class InverseJumpEdge extends InverseFlowEdge {
	
	private final JumpInsnNode jump;
	
	protected InverseJumpEdge(BasicBlock src, BasicBlock dst, JumpInsnNode jump) {
		super(src, dst);
		this.jump = jump;
	}
	@Override
	public String toString() {
		return String.format("Jump[%s] #%s <- #%s", Printer.OPCODES[jump.opcode()], src.getId(), dst.getId());
	}
	
	@Override
	public String toGraphString() {
		return "TODO";
	}
	
	@Override
	public FlowEdge clone(BasicBlock src, BasicBlock dst) {
		return new InverseJumpEdge(dst, src, jump);
	}
}