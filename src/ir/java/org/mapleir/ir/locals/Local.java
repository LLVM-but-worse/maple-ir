package org.mapleir.ir.locals;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class Local implements Comparable<Local> {

	private final AtomicInteger base; // maxLocals
	private final boolean stack;
	private final int index;
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
//		return stack ? getBase() + index : index;
		return index;
	}

	private static final boolean DEBUG_PRINT = false;
	@Override
	public String toString() {
		if (DEBUG_PRINT)
			return (stack ? "S" : "L") + /*"var" +*/ index;
		return (stack ? "s" : "l") + "var" + index;
	}

	public boolean isTempLocal() {
		return tempLocal;
	}

	public void setTempLocal(boolean temp) {
		if (temp && !isStack())
			throw new UnsupportedOperationException("Local variables cannot be stored in a temp lvar");
		tempLocal = temp;
	}

	public boolean isStoredInLocal() {
		return !isStack() || isTempLocal();
	}

	@Override
	public int compareTo(Local o) {
		return (toString().compareTo(o.toString()));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + index;
		result = prime * result + (stack ? 1 : 0);
		return result;
	}


	@Override
	public boolean equals(Object o) {
		return (o instanceof Local) && o.hashCode() == hashCode();
	}
}