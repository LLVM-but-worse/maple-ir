package org.mapleir.ir.antlr.scope.processor.entry;

import org.mapleir.ir.antlr.directive.DirectiveToken;

public interface ProcessorEntry {
	boolean handle(DirectiveToken token);
}