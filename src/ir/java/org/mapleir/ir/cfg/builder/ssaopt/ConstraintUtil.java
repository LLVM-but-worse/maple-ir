package org.mapleir.ir.cfg.builder.ssaopt;

import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.stmt.Statement;

public class ConstraintUtil implements Opcode {

	public static boolean isInvoke(Statement e) {
		int opcode = e.getOpcode();
		/* INIT_OBJ contains a folded constructor call. */
		return opcode == INVOKE || opcode == DYNAMIC_INVOKE || opcode == INIT_OBJ;
	}

	public static boolean isUncopyable(Statement stmt) {
		if(isUncopyable0(stmt.getOpcode())) {
			return true;
		}
		
		for(Statement s : stmt.enumerate()) {
			int opcode = s.getOpcode();
			if(isUncopyable0(opcode)) {
				return true;
			}
		}
		return false;
	}
	
	private static boolean isUncopyable0(int opcode) {
		switch (opcode) {
			case INVOKE:
			case DYNAMIC_INVOKE:
			case INIT_OBJ:
			case UNINIT_OBJ:
			case NEW_ARRAY:
			case CATCH:
			case EPHI:
			case PHI:
				return true;
		};
		return false;
	}
}