package org.mapleir.ir.antlr.scope;

import org.mapleir.ir.antlr.model.ClassDeclaration;

public class ClassScope extends Scope {
	
	private final ClassDeclaration decl;
	
	public ClassScope(Scope scope) {
		super(scope.driver, scope);
		decl = new ClassDeclaration();
	}
	
	public ClassDeclaration getClassDecl() {
		return decl;
	}
}