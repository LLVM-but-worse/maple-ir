package org.rsdeob.stdlib.cfg.statopt;

public class Variable {
	private final int index;
	private final boolean stackVar;

	public Variable(int index, boolean stackVar) {
		this.index = index;
		this.stackVar = stackVar;
	}

	public int getIndex() {
		return index;
	}

	public boolean isStackVar() {
		return stackVar;
	}

	@Override
	public String toString() {
		return (stackVar ? "svar" : "lvar") + index;
	}

	@Override
	public int hashCode() {
		return 37 * index + 41 * (stackVar? 's' : 'l');
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Variable other = (Variable) obj;
		return other.hashCode() == this.hashCode();
	}
}

