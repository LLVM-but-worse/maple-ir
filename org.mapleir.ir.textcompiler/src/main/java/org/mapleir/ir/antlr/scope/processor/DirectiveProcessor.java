package org.mapleir.ir.antlr.scope.processor;

import org.mapleir.ir.antlr.directive.DirectiveToken;

public interface DirectiveProcessor {

	void process(DirectiveToken tok);
}