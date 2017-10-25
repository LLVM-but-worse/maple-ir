package org.mapleir.ir.antlr.model;

public abstract class ClassMemberDeclaration {

	private int access;
	private String name;
	private String desc;

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

	public String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}
}