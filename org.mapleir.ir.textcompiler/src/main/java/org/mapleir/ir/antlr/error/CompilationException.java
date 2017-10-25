package org.mapleir.ir.antlr.error;

import org.mapleir.ir.antlr.source.SourcePosition;

@SuppressWarnings("serial")
public class CompilationException extends Exception implements CompilationProblem {

	private final SourcePosition pos;

	public CompilationException(String message, SourcePosition pos) {
		super(message);
		this.pos = pos;
	}

	public CompilationException(Throwable cause, SourcePosition pos) {
		super(cause);
		this.pos = pos;
	}

	public CompilationException(String message, Throwable cause, SourcePosition pos) {
		super(message, cause);
		this.pos = pos;
	}

	@Override
	public SourcePosition getPosition() {
		return pos;
	}
}