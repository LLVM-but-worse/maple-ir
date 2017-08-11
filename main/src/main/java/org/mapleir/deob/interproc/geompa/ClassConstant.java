package org.mapleir.deob.interproc.geompa;

import org.mapleir.ir.TypeUtils;
import org.objectweb.asm.Type;

public class ClassConstant {
	public final String value;

	public ClassConstant(String s) {
		value = s;
	}
	
	public boolean isRefType() {
		return value.startsWith("L") && value.endsWith(";");
	}

	public Type toSootType() {
		return Type.getType(value);
	}

	@Override
	public boolean equals(Object c) {
		return (c instanceof ClassConstant && ((ClassConstant) c).value.equals(value));
	}

	@Override
	public int hashCode() {
		return value.hashCode();
	}

	@Override
	public String toString() {
		return "class " + value;
	}

	public String getValue() {
		return value;
	}

	public Type getType() {
		return TypeUtils.CLASS;
	}
}