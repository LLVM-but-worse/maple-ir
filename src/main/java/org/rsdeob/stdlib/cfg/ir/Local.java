package org.rsdeob.stdlib.cfg.ir;

public class Local implements Comparable<Local> {

	private final int base;
	private final int index;
	private final boolean stack;
	
	public Local(int base, int index) {
		this(base, index, false);
	}
	
	public Local(int base, int index, boolean stack) {
		this.base = base;
		this.index = index;
		this.stack = stack;
	}
	
	public int getBase() {
		return base;
	}
	
	public boolean isStack() {
		return stack;
	}
	
	public int getIndex() {
		return index;
	}
	
	public int getCodeIndex() {
		return (stack ? base - 1 : 0) + index;
	}
	
	@Override
	public String toString() {
		return (stack ? "s" : "l") + "var" + index;
	}

	@Override
	public int compareTo(Local o) {
		return (toString().compareTo(o.toString()));
	}
}