package org.rsdeob.stdlib.cfg.edge;

import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.util.Printer;
import org.rsdeob.stdlib.cfg.BasicBlock;

public abstract class JumpEdge extends FlowEdge {
	
	public final JumpInsnNode jump;
	
	public JumpEdge(BasicBlock src, BasicBlock dst, JumpInsnNode jump, InverseJumpEdge inverse) {
		super(src, dst, inverse);
		this.jump = jump;
	}
	
	@Override
	public String toGraphString() {
		return Printer.OPCODES[jump.opcode()];
	}
	@Override
	public String toString() {
		return String.format("Jump[%s] #%s -> #%s", Printer.OPCODES[jump.opcode()], src.getId(), dst.getId());
	}
}