package org.mapleir.deob.interproc.geompa;

import org.mapleir.ir.TypeCone;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

public class GlobalVarNode extends VarNode {
	GlobalVarNode(PAG pag, Object variable, TypeCone tc) {
		super(pag, variable, tc);
	}

	public ClassNode getDeclaringClass() {
		if (variable instanceof FieldNode) {
			return ((FieldNode) variable).owner;
		}
		return null;
	}

	@Override
	public String toString() {
		return "GlobalVarNode " + getNumber() + " " + variable;
	}
}