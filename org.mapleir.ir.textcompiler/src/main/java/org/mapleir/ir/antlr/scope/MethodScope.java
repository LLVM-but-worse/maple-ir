package org.mapleir.ir.antlr.scope;

import org.mapleir.ir.antlr.model.MethodDeclaration;

public class MethodScope extends ClassMemberScope<MethodDeclaration> {

	public MethodScope(ClassScope parent) {
		super(parent, new MethodDeclaration());
	}
	
	@Override
	protected void registerProcessors() {
		super.registerProcessors();
		
		
	}
}