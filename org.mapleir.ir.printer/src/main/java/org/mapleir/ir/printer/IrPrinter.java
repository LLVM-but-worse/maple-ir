package org.mapleir.ir.printer;

import org.mapleir.ir.printer.indentation.IndentationProvider;

public abstract class IrPrinter<E> {
	private IndentationProvider indentationProvider;

	public IrPrinter(IndentationProvider indentationProvider) {
		this.indentationProvider = indentationProvider;
	}

	public abstract String toIr(E e);

	public String toIr(E e, int indentationLevel) {
		return indentationProvider.indent(toIr(e), indentationLevel);
	}
}
