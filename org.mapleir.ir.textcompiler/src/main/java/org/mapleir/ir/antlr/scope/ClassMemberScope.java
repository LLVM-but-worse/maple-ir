package org.mapleir.ir.antlr.scope;

import org.mapleir.ir.antlr.model.ClassMemberDeclaration;
import org.mapleir.ir.antlr.scope.processor.typematch.ClassTypeMatcher;
import org.mapleir.ir.antlr.scope.processor.typematch.HomogeneousCollectionTypeMatcher;

public abstract class ClassMemberScope<T extends ClassMemberDeclaration> extends Scope {

	private final T declaration;

	public ClassMemberScope(ClassScope parent, T declaration) {
		super(parent.driver, parent);
		this.declaration = declaration;
	}
	
	@Override
	protected void registerProcessors() {
		super.registerProcessors();

		processorManager.registerProcessor("access", new ClassTypeMatcher(Integer.class),
				(t) -> declaration.setAccess(t.getValue().getValueUnsafe()));
	}

	public T getDeclaration() {
		return declaration;
	}
	
	@Override
	public ClassScope getParent() {
		return (ClassScope) super.getParent();
	}
}