package org.rsdeob.stdlib.cfg.ir.stat;

import org.objectweb.asm.MethodVisitor;
import org.rsdeob.stdlib.cfg.ir.stat.header.HeaderStatement;
import org.rsdeob.stdlib.cfg.ir.transform.impl.CodeAnalytics;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;

import com.sun.xml.internal.ws.org.objectweb.asm.Opcodes;

public class UnconditionalJumpStatement extends Statement {

	private HeaderStatement target;

	public UnconditionalJumpStatement(HeaderStatement target) {
		this.target = target;
	}

	public HeaderStatement getTarget() {
		return target;
	}

	@Override
	public void onChildUpdated(int ptr) {
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print("goto " + target.getHeaderId());
	}

	@Override
	public void toCode(MethodVisitor visitor, CodeAnalytics analytics) {
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
			return target.equivalent(jump.target);
		}
		return false;
	}
}