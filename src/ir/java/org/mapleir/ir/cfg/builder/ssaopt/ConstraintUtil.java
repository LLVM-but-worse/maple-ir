package org.mapleir.ir.cfg.builder.ssaopt;

import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.stmt.Statement;

public class ConstraintUtil {

	public static boolean isInvoke(Statement e) {
		int opcode = e.getOpcode();
		return opcode == Opcode.INVOKE || opcode == Opcode.DYNAMIC_INVOKE || opcode == Opcode.INIT_OBJ;
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
		return opcode == Opcode.INVOKE || opcode == Opcode.DYNAMIC_INVOKE || opcode == Opcode.INIT_OBJ
				|| opcode == Opcode.UNINIT_OBJ;
	}
}