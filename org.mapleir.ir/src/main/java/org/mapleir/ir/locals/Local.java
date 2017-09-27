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
		if (index < 0)
			throw new IllegalArgumentException("Index underflow; hashCode collision possible " + index);
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
		if(stack && !o.stack) {
			return -1;
		} else if(!stack && o.stack) {
			return 1;
		}
		return Integer.compare(index, o.index);
	}

	@Override
	public int hashCode() {
		return ((stack ? 0 : 1) << 31) | index;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		else if (o instanceof Local) {
			Local other = (Local) o;
			return stack == other.stack && index == other.index;
		} else
			return false;
	}
}