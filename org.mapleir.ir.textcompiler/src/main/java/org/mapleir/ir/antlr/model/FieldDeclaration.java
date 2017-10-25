package org.mapleir.ir.antlr.model;

public class FieldDeclaration extends ClassMemberDeclaration {

	private Object defaultValue;
	
	public FieldDeclaration() {	
	}
	
	public Object getDefaultValue() {
		return defaultValue;
	}
	
	public void setDefaultValue(Object defaultValue) {
		this.defaultValue = defaultValue;
	}
}