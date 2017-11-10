package org.mapleir.ir.printer;

import org.mapleir.ir.printer.indentation.IndentationProvider;

public abstract class Printer<E> {
	private IndentationProvider indentationProvider;

	public Printer(IndentationProvider indentationProvider) {
		this.indentationProvider = indentationProvider;
	}

	public abstract String toString(E e);

	public String toString(E e, int indentationLevel) {
		return indentationProvider.indent(toString(e), indentationLevel);
	}
}
