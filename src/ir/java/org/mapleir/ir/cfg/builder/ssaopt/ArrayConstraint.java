package org.mapleir.ir.cfg.builder.ssaopt;

import org.mapleir.ir.code.stmt.Statement;

public class ArrayConstraint implements Constraint {
	
	@Override
	public boolean fails(Statement s) {
//		return s.getOpcode() == Opcode.ARRAY_STORE || ConstraintUtil.isInvoke(s);
//		return false;
		return true;
	}
}