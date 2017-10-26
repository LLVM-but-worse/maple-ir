package org.mapleir.ir.antlr.directive;

import org.mapleir.ir.antlr.source.SourcePosition;

public class DirectiveToken {

	private final SourcePosition keySourcePosition;
	private final String key;
	private final DirectiveValue value;

	public DirectiveToken(SourcePosition keySourcePosition, String key, DirectiveValue value) {
		this.keySourcePosition = keySourcePosition;
		this.key = key;
		this.value = value;
	}

	public SourcePosition getKeySourcePosition() {
		return keySourcePosition;
	}

	public String getKey() {
		return key;
	}

	public DirectiveValue getValue() {
		return value;
	}

	@Override
	public String toString() {
		return "DirectiveToken [keySourcePosition=" + keySourcePosition + ", key=" + key + ", value=" + value + "]";
	}
}