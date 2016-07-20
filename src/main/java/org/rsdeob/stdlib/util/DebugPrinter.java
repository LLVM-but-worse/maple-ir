package org.rsdeob.stdlib.util;

import org.rsdeob.stdlib.ir.stat.Statement;

public class DebugPrinter {
	public static final boolean DEBUG = true;
	
	public static void dbgPrintln(int id, String s) {
		if (!DEBUG)
			return;
		int hundreds = id / 100;
		int tens = (id -= 100 * hundreds) / 10;
		int ones = (id -= 10 * tens);
		System.out.println("[dbg " + hundreds + "." + tens + "." + ones + "] " + s);
	}
	
	public static void dbgStmt(Statement stmt) {
		if (!DEBUG)
			return;
		System.err.println("[dbg] " + stmt.getId() + ". " + stmt.toString() + " (class=" + stmt.getClass().getSimpleName() + ")");
	}
}
