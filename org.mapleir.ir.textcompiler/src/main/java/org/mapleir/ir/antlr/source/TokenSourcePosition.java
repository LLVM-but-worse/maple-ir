package org.mapleir.ir.antlr.source;

import org.antlr.v4.runtime.Token;

public class TokenSourcePosition extends SourcePosition {

	public final Token token;

	public TokenSourcePosition(int line, int column, int offset, Token token) {
		super(line, column, offset);
		this.token = token;
	}

	@Override
	public String getText() {
		return token.getText();
	}

	@Override
	public TokenSourcePosition clone(int line, int column, int offset) {
		return new TokenSourcePosition(line, column, offset, token);
	}

	@Override
	public String toString() {
		return "TokenSourcePosition [line=" + line + ", column=" + column + ", offset=" + tokenOffset + ", token=" + token
				+ "]";
	}
}