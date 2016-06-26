package org.rsdeob.stdlib.cfg.ir;

import java.util.concurrent.atomic.AtomicInteger;

public class Local implements Comparable<Local> {

	private final AtomicInteger base; // maxLocals
	private final int index;
	private final boolean stack;
	private boolean tempLocal;
	
	public Local(AtomicInteger base, int index) {
		this(base, index, false);
	}
	
	public Local(AtomicInteger base, int index, boolean stack) {
		this.base = base;
		this.index = index;
		this.stack = stack;
	}
	
	public int getBase() {
		return base.get();
	}

	public boolean isStack() {
		return stack;
	}
	
	public int getIndex() {
		return index;
	}
	
	public int getCodeIndex() {
		return stack ? getBase() + index : index;
	}
	
	@Override
	public String toString() {
		return (stack ? "s" : "l") + "var" + index;
	}

	public boolean isTempLocal() {
		return tempLocal;
	}

	public void setTempLocal(boolean temp) {
		if (temp && !isStack())
			throw new UnsupportedOperationException("Local variables cannot be stored in a temp lvar");
		this.tempLocal = temp;
	}

	public boolean isStoredInLocal() {
		return !isStack() || isTempLocal();
	}

	@Override
	public int compareTo(Local o) {
		return (toString().compareTo(o.toString()));
	}
}