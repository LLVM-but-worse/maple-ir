package org.mapleir.ir.antlr.error;

import org.mapleir.ir.antlr.source.SourcePosition;

public interface CompilationProblem {

	SourcePosition getPosition();
	
	String getMessage();
}