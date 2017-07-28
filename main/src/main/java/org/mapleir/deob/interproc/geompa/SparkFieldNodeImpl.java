package org.mapleir.deob.interproc.geompa;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;

public class SparkFieldNodeImpl implements SparkField {

	private final FieldNode field;
	private final Type type;
	
	public SparkFieldNodeImpl(FieldNode field) {
		super();
		this.field = field;
		type = Type.getType(field.desc);
	}

	public FieldNode getField() {
		return field;
	}

	@Override
	public Type getType() {
		return type;
	}
}