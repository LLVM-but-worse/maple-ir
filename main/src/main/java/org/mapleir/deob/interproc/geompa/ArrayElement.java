package org.mapleir.deob.interproc.geompa;

public class ArrayElement implements SparkField {
	public ArrayElement(Singletons.Global g) {
	}

	public static ArrayElement v() {
		return G.v().soot_jimple_spark_pag_ArrayElement();
	}

	public ArrayElement() {
		Scene.v().getFieldNumberer().add(this);
	}

	public final int getNumber() {
		return number;
	}

	public final void setNumber(int number) {
		this.number = number;
	}

	public Type getType() {
		return RefType.v("java.lang.Object");
	}

	private int number = 0;
}