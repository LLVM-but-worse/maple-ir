package org.rsdeob.stdlib.cfg.statopt;

import org.rsdeob.stdlib.cfg.expr.StackLoadExpression;

public class Variable {
	private final int index;
	private final boolean stackVar;

	public Variable(int index, boolean stackVar) {
		this.index = index;
		this.stackVar = stackVar;
	}

	public Variable(StackLoadExpression expr) {
		this(expr.getIndex(), expr.isStackVariable());
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
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Variable variable = (Variable) o;

		if (index != variable.index) return false;
		return stackVar == variable.stackVar;

	}

	@Override
	public int hashCode() {
		int result = index;
		result = 31 * result + (stackVar ? 1 : 0);
		return result;
	}
}

