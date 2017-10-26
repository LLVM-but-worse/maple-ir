package org.mapleir.ir.antlr.scope.processor.typematch;

import org.mapleir.ir.antlr.directive.DirectiveValue;

public class ClassTypeMatcher implements TypeMatcher {

	private final Class<?> clazz;

	public ClassTypeMatcher(Class<?> clazz) {
		this.clazz = clazz;
	}

	@Override
	public boolean accept(DirectiveValue val) {
		Object actualValue = val.getValue();

		if (actualValue != null) {
			Class<?> valueType = actualValue.getClass();
			return clazz.isAssignableFrom(valueType);
		} else {
			return clazz == null;
		}
	}
}