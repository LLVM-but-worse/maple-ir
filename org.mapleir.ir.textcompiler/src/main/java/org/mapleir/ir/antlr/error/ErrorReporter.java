package org.mapleir.ir.antlr.error;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.mapleir.ir.antlr.source.SourcePosition;

public interface ErrorReporter {

	SourcePosition makeSourcePositionOnly(Token token);
	
	SourcePosition makeSourcePositionOnly(ParseTree parseTree);
	
	SourcePosition newSourcePosition(Token token);
	
	SourcePosition newSourcePosition(ParseTree parseTree);
	
	SourcePosition newSourcePosition(int charOffset);
	
	void pushSourcePosition(SourcePosition pos);
	
	void popSourcePosition(SourcePosition expected);
	
	default void error(String msg) {
		error(msg, 0);
	}
	
	void error(String msg, int charOffset);
	
	default void error(Throwable cause) {
		error(cause, 0);
	}
	
	void error(Throwable cause, int charOffset);
	
	default void error(Throwable cause, String msg) {
		error(cause, msg, 0);
	}
	
	void error(Throwable cause, String msg, int charOffset);
	
	default void warn(String msg) {
		warn(msg, 0);
	}
	
	void warn(String msg, int charOffset);
}