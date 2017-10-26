package org.mapleir.ir.antlr.scope;

import java.util.List;

import org.mapleir.ir.antlr.directive.DirectiveDictValue;
import org.mapleir.ir.antlr.model.MethodDeclaration;
import org.mapleir.ir.antlr.scope.processor.typematch.ClassTypeMatcher;
import org.mapleir.ir.antlr.scope.processor.typematch.DictTypeMatcher;
import org.mapleir.ir.antlr.scope.processor.typematch.HomogeneousCollectionTypeMatcher;
import org.mapleir.ir.antlr.scope.processor.typematch.TypeMatcher;

public class MethodScope extends ClassMemberScope<MethodDeclaration> {

	public MethodScope(ClassScope parent) {
		super(parent, new MethodDeclaration());
	}
	
	@Override
	protected void registerProcessors() {
		super.registerProcessors();

		TypeMatcher stringMatcher = new ClassTypeMatcher(String.class);
		
		DictTypeMatcher tableEntryMatcher = new DictTypeMatcher()
				.add("start", stringMatcher)
				.add("end", stringMatcher)
				.add("handler", stringMatcher);
		
		processorManager.registerProcessor("handlers", new HomogeneousCollectionTypeMatcher(tableEntryMatcher),
				(t) -> (t.getValue().<List<DirectiveDictValue>>getValueUnsafe()).forEach(v -> print(v)));
		
	}
	
	private void print(DirectiveDictValue dict) {
		System.out.println("handler:");
		System.out.println("  s: " + dict.getValue("start"));
		System.out.println("  e: " + dict.getValue("end"));
		System.out.println("  h: " + dict.getValue("handler"));
	}
}