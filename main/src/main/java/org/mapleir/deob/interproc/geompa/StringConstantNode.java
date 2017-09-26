package org.mapleir.deob.interproc.geompa;

import org.mapleir.ir.TypeCone;
import org.mapleir.ir.TypeUtils;

public class StringConstantNode extends AllocNode {

	StringConstantNode(PAG pag, String sc) {
		super(pag, sc, TypeCone.getType(TypeUtils.STRING), null);
	}

	@Override
	public String toString() {
		return "StringConstantNode " + getNumber() + " " + newExpr;
	}

	public String getString() {
		return (String) newExpr;
	}
}