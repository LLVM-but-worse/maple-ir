package org.mapleir.ir.code.stmt;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.objectweb.asm.MethodVisitor;

import com.sun.xml.internal.ws.org.objectweb.asm.Opcodes;

public class UnconditionalJumpStatement extends Statement {

	private BasicBlock target;

	public UnconditionalJumpStatement(BasicBlock target) {
		super(UNCOND_JUMP);
		this.target = target;
	}

	public BasicBlock getTarget() {
		return target;
	}

	@Override
	public void onChildUpdated(int ptr) {
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print("goto " + target.getId());
	}

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		visitor.visitJumpInsn(Opcodes.GOTO, target.getLabel());		
	}

	@Override
	public boolean canChangeFlow() {
		return true;
	}

	@Override
	public boolean canChangeLogic() {
		return false;
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		return false;
	}

	@Override
	public Statement copy() {
		return new UnconditionalJumpStatement(target);
	}

	@Override
	public boolean equivalent(Statement s) {
		if(s instanceof UnconditionalJumpStatement) {
			UnconditionalJumpStatement jump = (UnconditionalJumpStatement) s;
			return target.getNumericId() == jump.target.getNumericId();
		}
		return false;
	}
}