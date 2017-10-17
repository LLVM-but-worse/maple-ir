package org.mapleir.ir.antlr;

public class CompilationException extends RuntimeException {
	private static final long serialVersionUID = -8960623322577410119L;

	private final int line, col;

	public CompilationException(int line, int col, String message) {
		super(message);
		this.line = line;
		this.col = col;
	}

	public CompilationException(int line, int col, String message, Exception e) {
		super(message, e);
		this.line = line;
		this.col = col;
	}

	public int getLine() {
		return line;
	}

	public int getColumn() {
		return col;
	}

	@Override
	public String toString() {
		return String.format("Error compiling at line %d%s, %s", line, col == -1 ? "" : (" (col:" + col + ")"),
				super.toString());
	}
}