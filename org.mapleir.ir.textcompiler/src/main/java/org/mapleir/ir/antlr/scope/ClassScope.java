package org.mapleir.ir.antlr.scope;

import org.mapleir.ir.antlr.model.ClassDeclaration;
import org.mapleir.ir.antlr.scope.processor.typematch.ClassTypeMatcher;
import org.mapleir.ir.antlr.scope.processor.typematch.HomogeneousCollectionTypeMatcher;

public class ClassScope extends Scope {
	
	private final ClassDeclaration decl;
	
	public ClassScope(Scope scope) {
		super(scope.driver, scope);
		decl = new ClassDeclaration();
	}
	
	@Override
	protected void registerProcessors() {
		super.registerProcessors();

		processorManager.registerProcessor("exceptions",
				new HomogeneousCollectionTypeMatcher(new ClassTypeMatcher(String.class)),
				(t) -> decl.getExceptions().add(t.getValue().getValueUnsafe()));
	}
	
	public ClassDeclaration getClassDecl() {
		return decl;
	}
}