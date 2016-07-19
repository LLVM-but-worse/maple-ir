package org.rsdeob.stdlib.ir.locals;

import java.util.concurrent.atomic.AtomicInteger;

public class VersionedLocal extends Local {

	private int subscript;
	
	public VersionedLocal(AtomicInteger base, int index, int subscript) {
		super(base, index);
		this.subscript = subscript;
	}
	
	public VersionedLocal(AtomicInteger base, int index, int subscript, boolean stack) {
		super(base, index, stack);
		this.subscript = subscript;
	}
	
	public int getSubscript() {
		return subscript;
	}
	
	@Override
	public int getCodeIndex() {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public String toString() {
		return super.toString() + "_" + subscript;
	}
	
	public boolean isVersionOf(Local l) {
		return l.isStack() == isStack() && l.getIndex() == getIndex();
	}
}