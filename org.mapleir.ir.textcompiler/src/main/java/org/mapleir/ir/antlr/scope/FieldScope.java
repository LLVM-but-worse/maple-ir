package org.mapleir.ir.antlr.scope;

import org.mapleir.ir.antlr.model.FieldDeclaration;

public class FieldScope extends ClassMemberScope<FieldDeclaration> {

	public FieldScope(ClassScope parent) {
		super(parent, new FieldDeclaration());
	}
}