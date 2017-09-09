package org.mapleir.deob.interproc.geompa;

import org.mapleir.ir.TypeCone;
import org.mapleir.ir.TypeUtils;

public class ClassConstantNode extends AllocNode {

	ClassConstantNode(PAG pag, ClassConstant cc) {
		super(pag, cc, TypeCone.get(TypeUtils.CLASS), null);
	}

	public ClassConstant getClassConstant() {
		return (ClassConstant) newExpr;
	}

	@Override
	public String toString() {
		return "ClassConstantNode " + getNumber() + " " + newExpr;
	}
}