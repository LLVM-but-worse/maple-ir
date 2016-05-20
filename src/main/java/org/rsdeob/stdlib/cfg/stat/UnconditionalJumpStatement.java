package org.rsdeob.stdlib.cfg.stat;

import org.objectweb.asm.MethodVisitor;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;

import com.sun.xml.internal.ws.org.objectweb.asm.Opcodes;

public class UnconditionalJumpStatement extends Statement {

	private BasicBlock target;

	public UnconditionalJumpStatement(BasicBlock target) {
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
	public void toCode(MethodVisitor visitor) {
		visitor.visitJumpInsn(Opcodes.GOTO, target.getLabel().getLabel());		
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
}