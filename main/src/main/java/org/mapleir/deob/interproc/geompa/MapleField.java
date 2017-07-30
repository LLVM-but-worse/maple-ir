package org.mapleir.deob.interproc.geompa;

import org.objectweb.asm.tree.FieldNode;

public class MapleField {
	private final FieldNode fieldNode;
	
	public MapleField(FieldNode fieldNode) {
		this.fieldNode = fieldNode;
	}
	
	public MapleField field() {
		return this;
	}
	
	public FieldNode getFieldNode() {
		return fieldNode;
	}
}
