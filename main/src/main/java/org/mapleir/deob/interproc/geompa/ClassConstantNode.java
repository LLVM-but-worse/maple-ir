package org.mapleir.deob.interproc.geompa;

import org.mapleir.ir.TypeUtils;

public class ClassConstantNode extends AllocNode {

	ClassConstantNode(PAG pag, ClassConstant cc) {
		super(pag, cc, TypeUtils.CLASS, null);
	}

	public ClassConstant getClassConstant() {
		return (ClassConstant) newExpr;
	}

	@Override
	public String toString() {
		return "ClassConstantNode " + getNumber() + " " + newExpr;
	}
}