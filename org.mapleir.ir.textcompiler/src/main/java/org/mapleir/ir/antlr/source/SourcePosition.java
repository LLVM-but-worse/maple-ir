package org.mapleir.ir.antlr.source;

public abstract class SourcePosition {

	public final int line;
	public final int column;
	public final int tokenOffset;

	public SourcePosition(int line, int column, int tokenOffset) {
		this.line = line;
		this.column = column;
		this.tokenOffset = tokenOffset;
	}

	public abstract String getText();

	public abstract SourcePosition clone(int line, int column, int offset);

	@Override
	public String toString() {
		return "SourcePosition [line=" + line + ", column=" + column + ", offset=" + tokenOffset + "]";
	}
}