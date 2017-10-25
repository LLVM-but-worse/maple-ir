package org.mapleir.ir.antlr.error;

import org.mapleir.ir.antlr.source.SourcePosition;

public class CompilationWarning implements CompilationProblem {

	private final SourcePosition pos;
	private final String msg;

	public CompilationWarning(SourcePosition pos, String msg) {
		this.pos = pos;
		this.msg = msg;
	}

	@Override
	public SourcePosition getPosition() {
		return pos;
	}

	@Override
	public String getMessage() {
		return msg;
	}
}