package org.rsdeob.stdlib.ir.header;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.transform.impl.CodeAnalytics;

public abstract class HeaderStatement extends Statement {

	public abstract String getHeaderId();
	
	public abstract void resetLabel();
	
	public abstract Label getLabel();
	
	@Override
	public void onChildUpdated(int ptr) {
		
	}

	@Override
	public boolean changesIndentation() {
		return true;
	}
	
	@Override
	public void toString(TabbedStringWriter printer) {
		if(printer.getTabCount() > 0) {
			printer.untab();
		}
		printer.print(getHeaderId() + ":");
		printer.tab();
	}

	@Override
	public void toCode(MethodVisitor visitor, CodeAnalytics analytics) {
		visitor.visitLabel(getLabel());
	}

	@Override
	public boolean canChangeFlow() {
		return false;
	}

	@Override
	public boolean canChangeLogic() {
		throw new UnsupportedOperationException();
//		return false;
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		throw new UnsupportedOperationException();
	}
}