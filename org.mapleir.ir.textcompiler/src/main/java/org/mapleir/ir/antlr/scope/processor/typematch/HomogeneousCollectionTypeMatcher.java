package org.mapleir.ir.antlr.scope.processor.typematch;

import org.mapleir.ir.antlr.directive.DirectiveValue;
import org.mapleir.ir.antlr.directive.DirectiveValueList;

public class HomogeneousCollectionTypeMatcher implements TypeMatcher {

	private final TypeMatcher elementMatcher;

	public HomogeneousCollectionTypeMatcher(TypeMatcher elementMatcher) {
		this.elementMatcher = elementMatcher;
	}

	@Override
	public boolean accept(DirectiveValue val) {
		if (val instanceof DirectiveValueList) {
			DirectiveValueList list = (DirectiveValueList) val;

			for (DirectiveValue v : list) {
				if (!elementMatcher.accept(v)) {
					return false;
				}
			}

			return true;
		} else {
			return false;
		}
	}
}