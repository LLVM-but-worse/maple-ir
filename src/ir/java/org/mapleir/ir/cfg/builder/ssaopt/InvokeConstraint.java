package org.mapleir.ir.cfg.builder.ssaopt;

import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.stmt.Statement;

public class InvokeConstraint implements Constraint {
	@Override
	public boolean fails(Statement s) {
		return ConstraintUtil.isInvoke(s) || s.getOpcode() == Opcode.FIELD_STORE || s.getOpcode() == Opcode.ARRAY_STORE;
	}
}