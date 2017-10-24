package org.mapleir.ir.printer.indentation;

public interface IndentationProvider {
	String indent(String source, int indentationLevel);
}
