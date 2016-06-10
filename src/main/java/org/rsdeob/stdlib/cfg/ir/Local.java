package org.rsdeob.stdlib.cfg.ir;

public class Local implements Comparable<Local> {

	private final int index;
	private final boolean stack;
	
	public Local(int index) {
		this(index, false);
	}
	
	public Local(int index, boolean stack) {
		this.index = index;
		this.stack = stack;
	}
	
	public boolean isStack() {
		return stack;
	}
	
	public int getIndex() {
		return index;
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