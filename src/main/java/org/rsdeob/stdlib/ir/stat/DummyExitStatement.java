package org.rsdeob.stdlib.ir.stat;

import org.objectweb.asm.MethodVisitor;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.ir.transform.impl.CodeAnalytics;

public class DummyExitStatement extends Statement {

	@Override
	public void onChildUpdated(int ptr) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		
	}

	@Override
	public void toCode(MethodVisitor visitor, CodeAnalytics analytics) {
		
	}

	@Override
	public boolean canChangeFlow() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean canChangeLogic() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Statement copy() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean equivalent(Statement s) {
		throw new UnsupportedOperationException();
	}
}