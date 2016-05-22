package org.rsdeob.stdlib.cfg.statopt;

import org.rsdeob.stdlib.cfg.stat.Statement;

public class Assignment  { // also known as a 'copy' in academic terms
	private final Variable lhs;
	private final Statement rhs;

	public Assignment(Variable var, Statement stmt) {
		if (var == null)
			throw new IllegalArgumentException("Variable cannot be null!");
		this.lhs = var;
		this.rhs = stmt;
	}

	public Statement getStatement() {
		return rhs;
	}

	@Override
	public String toString() {
		return lhs.toString();
	}

	@Override
	public int hashCode() {
		return 31 * lhs.hashCode() + 43 * rhs.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Assignment other = (Assignment) obj;
		return other.hashCode() == this.hashCode();
	}
}