package org.mapleir.ir.antlr.model;

import java.util.ArrayList;
import java.util.List;

public class ClassDeclaration {

	private int access;
	private String name;
	private String superName;
	private List<String> interfaces;
	private List<FieldDeclaration> fields;
	
	public ClassDeclaration() {
		interfaces = new ArrayList<>();
		fields = new ArrayList<>();
	}

	public int getAccess() {
		return access;
	}

	public void setAccess(int access) {
		this.access = access;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getSuperName() {
		return superName;
	}

	public void setSuperName(String superName) {
		this.superName = superName;
	}

	public List<String> getInterfaces() {
		return interfaces;
	}

	public void setInterfaces(List<String> interfaces) {
		this.interfaces = interfaces;
	}

	public List<FieldDeclaration> getFields() {
		return fields;
	}

	public void setFields(List<FieldDeclaration> fields) {
		this.fields = fields;
	}
}