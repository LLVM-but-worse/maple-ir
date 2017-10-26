package org.mapleir.ir.antlr.scope.processor.typematch;

import org.mapleir.ir.antlr.directive.DirectiveValue;

public class AnyTypeMatcher implements TypeMatcher {
	@Override
	public boolean accept(DirectiveValue val) {
		return true;
	}
}
