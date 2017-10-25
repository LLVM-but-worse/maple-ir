package org.mapleir.ir.antlr.model;

import java.lang.reflect.Modifier;

import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.ir.locals.impl.VersionedLocal;

public class UpdateableLocalsPool extends LocalsPool {

	private int access;
	
	public UpdateableLocalsPool(int base) {
		super(base);
	}

	public int getAccess() {
		return access;
	}

	public void setAccess(int access) {
		this.access = access;
	}

	@Override
	public boolean isReservedRegister(Local l) {
		return isSelfReceiverRegister(l);
	}

	@Override
	public boolean isImplicitRegister(Local l) {
		return isSelfReceiverRegister(l);
	}
	
	private boolean isSelfReceiverRegister(Local l) {
		if(Modifier.isStatic(access)) {
			return false;
		}
		
		boolean isSelfReceiver = !l.isStack() && l.getIndex() == 0;
		
		if (l instanceof VersionedLocal) {
			VersionedLocal vl = (VersionedLocal) l;
			return isSelfReceiver && vl.getSubscript() == 0;
		} else {
			return isSelfReceiver;
		}
	}
}