package org.mapleir.ir.antlr.scope.processor.typematch;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.mapleir.ir.antlr.directive.DirectiveDictValue;
import org.mapleir.ir.antlr.directive.DirectiveValue;

public class DictTypeMatcher implements TypeMatcher {

	private final Map<String, TypeMatcher> elementMatchers;
	
	public DictTypeMatcher(Map<String, TypeMatcher> elementMatchers) {
		this.elementMatchers = elementMatchers;
	}
	
	public DictTypeMatcher() {
		this(new HashMap<>());
	}
	
	public DictTypeMatcher add(String key, TypeMatcher matcher) {
		elementMatchers.put(key, matcher);
		return this;
	}

	@Override
	public boolean accept(DirectiveValue val) {
		if(val instanceof DirectiveDictValue) {
			Map<String, DirectiveValue> dict = ((DirectiveDictValue) val).getValue();
			
			// TODO: add any/all etc
			
			for(Entry<String, TypeMatcher> e : elementMatchers.entrySet()) {
				DirectiveValue v = dict.get(e.getKey());
				
				if(v == null) {
					return false;
				}
				
				if(!e.getValue().accept(v)) {
					return false;
				}
			}
			
			return true;
		} else {
			return false;
		}
	}
}