package org.mapleir.deob.interproc.geompa;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

public class GlobalVarNode extends VarNode {
	GlobalVarNode(PAG pag, Object variable, Type t) {
		super(pag, variable, t);
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