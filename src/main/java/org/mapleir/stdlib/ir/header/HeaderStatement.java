package org.mapleir.stdlib.ir.header;

import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.mapleir.stdlib.ir.stat.Statement;
import org.mapleir.stdlib.ir.transform.impl.CodeAnalytics;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

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