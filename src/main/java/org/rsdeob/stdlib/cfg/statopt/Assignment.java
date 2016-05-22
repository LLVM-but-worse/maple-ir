package org.rsdeob.stdlib.cfg.statopt;

import org.rsdeob.stdlib.cfg.stat.StackDumpStatement;
import org.rsdeob.stdlib.cfg.stat.Statement;

public class Assignment  { // also known as a 'copy' in academic terms
	private final Variable lhs;
	private final Statement rhs;

	public Assignment(Variable var, Statement stmt) {
		if (var == null | stmt == null)
			throw new IllegalArgumentException("Neither variable nor statement can be null!");
		this.lhs = var;
		this.rhs = stmt;
	}

	public Assignment(StackDumpStatement stmt) {
		this(new Variable(stmt.getIndex(), stmt.isStackVariable()), stmt.getExpression());
	}

	public Variable getVariable() {
		return lhs;
	}

	public Statement getStatement() {
		return rhs;
	}

	@Override
	public String toString() {
		return lhs + " = " + rhs + ";";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Assignment that = (Assignment) o;

		if (!lhs.equals(that.lhs)) return false;
		return rhs.equals(that.rhs);

	}

	@Override
	public int hashCode() {
		int result = lhs.hashCode();
		result = 31 * result + rhs.hashCode();
		return result;
	}
}