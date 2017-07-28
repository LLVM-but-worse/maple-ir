package org.mapleir.deob.interproc.geompa;

import org.mapleir.ir.TypeUtils;
import org.objectweb.asm.Type;

public class ArrayElement implements SparkField {
	
	public static final ArrayElement INSTANCE = new ArrayElement();
	
	public ArrayElement() {
		// Scene.v().getFieldNumberer().add(this);
	}

	public final int getNumber() {
		return number;
	}

	public final void setNumber(int number) {
		this.number = number;
	}

	@Override
	public Type getType() {
		return TypeUtils.OBJECT_TYPE;
		// return RefType.v("java.lang.Object");
	}

	private int number = 0;
}