package org.mapleir.ir.printer;

import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.printer.indentation.IndentationProvider;

public class StmtPrinter extends Printer<Stmt> {
	public StmtPrinter(IndentationProvider indentationProvider) {
		super(indentationProvider);
	}

	@Override
	public String toString(Stmt e) {
		return "some stmt";
	}
}
