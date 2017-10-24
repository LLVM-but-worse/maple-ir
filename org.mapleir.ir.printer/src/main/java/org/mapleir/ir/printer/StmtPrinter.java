package org.mapleir.ir.printer;

import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.printer.indentation.IndentationProvider;

public class StmtPrinter extends IrPrinter<Stmt> {
	public StmtPrinter(IndentationProvider indentationProvider) {
		super(indentationProvider);
	}

	@Override
	public String toIr(Stmt e) {
		return "some stmt";
	}
}
