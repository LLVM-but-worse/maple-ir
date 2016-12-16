package org.mapleir.ir.cfg.builder.ssaopt;

import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.FieldLoadExpression;
import org.mapleir.ir.code.stmt.FieldStoreStatement;
import org.mapleir.ir.code.stmt.Statement;

public class FieldConstraint implements Constraint {
	private final String key;
	
	public FieldConstraint(FieldLoadExpression le) {
		key = le.getName() + "." + le.getDesc();
	}
	
	@Override
	public boolean fails(Statement s) {
		if(s.getOpcode() == Opcode.FIELD_STORE) {
			FieldStoreStatement store = (FieldStoreStatement) s;
			String key2 = store.getName() + "." + store.getDesc();
			if(key2.equals(key)) {
				return true;
			}
		} else if(ConstraintUtil.isInvoke(s)) {
			return true;
		}
		return false;
	}
}