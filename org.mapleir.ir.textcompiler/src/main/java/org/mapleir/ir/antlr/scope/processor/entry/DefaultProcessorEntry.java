package org.mapleir.ir.antlr.scope.processor.entry;

import org.mapleir.ir.antlr.directive.DirectiveToken;
import org.mapleir.ir.antlr.directive.DirectiveValue;
import org.mapleir.ir.antlr.scope.processor.DirectiveProcessor;
import org.mapleir.ir.antlr.scope.processor.typematch.TypeMatcher;

public class DefaultProcessorEntry implements ProcessorEntry {
	private final String key;
	private final TypeMatcher typeMatcher;
	private final DirectiveProcessor processor;
	
	public DefaultProcessorEntry(String key, TypeMatcher typeMatcher, DirectiveProcessor processor) {
		this.key = key;
		this.typeMatcher = typeMatcher;
		this.processor = processor;
	}

	@Override
	public boolean handle(DirectiveToken token) {
		DirectiveValue val = token.getValue();
		
		if(typeMatcher.accept(val)) {
			processor.process(token);
			return true;
		} else {
			return false;
		}
	}
	
	public String getKey() {
		return key;
	}
}