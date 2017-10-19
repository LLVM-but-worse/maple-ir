package org.mapleir.ir.locals.impl;

import java.lang.reflect.Modifier;

import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsPool;
import org.objectweb.asm.tree.MethodNode;

public class MethodNodeLocalsPool extends LocalsPool {

	private final MethodNode method;
	
	public MethodNodeLocalsPool(int base, MethodNode method) {
		super(base);
		this.method = method;
	}

	@Override
	public boolean isReservedRegister(Local l) {
		return isSelfReceiverRegister(l);
	}

	@Override
	public boolean isImplicitRegister(Local l) {
		return isSelfReceiverRegister(l);
	}

	protected boolean isSelfReceiverRegister(Local l) {
		if(Modifier.isStatic(method.access)) {
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