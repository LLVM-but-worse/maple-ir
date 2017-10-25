package org.mapleir.ir.antlr.source;

import org.antlr.v4.runtime.tree.ParseTree;

public class ParseTreeSourcePosition extends SourcePosition {

	public final ParseTree parseTree;

	public ParseTreeSourcePosition(int line, int column, int offset, ParseTree parseTree) {
		super(line, column, offset);
		this.parseTree = parseTree;
	}

	@Override
	public String getText() {
		return parseTree.getText();
	}

	@Override
	public ParseTreeSourcePosition clone(int line, int column, int offset) {
		return new ParseTreeSourcePosition(line, column, offset, parseTree);
	}

	@Override
	public String toString() {
		return "ParseTreeSourcePosition [line=" + line + ", column=" + column + ", offset=" + tokenOffset + ", parseTree="
				+ parseTree + "]";
	}
}
