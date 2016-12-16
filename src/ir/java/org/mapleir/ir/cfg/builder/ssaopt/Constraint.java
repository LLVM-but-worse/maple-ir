package org.mapleir.ir.cfg.builder.ssaopt;

import org.mapleir.ir.code.stmt.Statement;

public interface Constraint {
	boolean fails(Statement s);
}