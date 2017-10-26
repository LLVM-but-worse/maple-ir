package org.mapleir.ir.antlr.scope.processor.typematch;

import org.mapleir.ir.antlr.directive.DirectiveValue;

public interface TypeMatcher {
	boolean accept(DirectiveValue val);
}