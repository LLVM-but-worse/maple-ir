package org.mapleir.ir.antlr.directive;

import org.mapleir.ir.antlr.source.SourcePosition;

public class DirectiveValue {

	private final SourcePosition valueSoucePosition;
	private final Object value;

	public DirectiveValue(SourcePosition valueSoucePosition, Object value) {
		this.valueSoucePosition = valueSoucePosition;
		this.value = value;
	}

	public SourcePosition getValueSoucePosition() {
		return valueSoucePosition;
	}

	public Object getValue() {
		return value;
	}
	
	@SuppressWarnings("unchecked")
	public <T> T getValueUnsafe() {
		return (T) value;
	}
	
	@Override
	public String toString() {
		return String.valueOf(value);
	}
}