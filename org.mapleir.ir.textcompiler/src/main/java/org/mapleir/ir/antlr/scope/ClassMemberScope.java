package org.mapleir.ir.antlr.scope;

import org.mapleir.ir.antlr.model.ClassMemberDeclaration;

public abstract class ClassMemberScope<T extends ClassMemberDeclaration> extends Scope {

	private final T declaration;

	public ClassMemberScope(ClassScope parent, T declaration) {
		super(parent.driver, parent);
		this.declaration = declaration;
	}

	public T getDeclaration() {
		return declaration;
	}
	
	@Override
	public ClassScope getParent() {
		return (ClassScope) super.getParent();
	}
}