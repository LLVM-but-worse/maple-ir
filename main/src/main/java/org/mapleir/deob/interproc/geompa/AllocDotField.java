package org.mapleir.deob.interproc.geompa;

public class AllocDotField extends PointsToNode {

	protected AllocNode base;
	protected SparkField field;

	AllocDotField(PAG pag, AllocNode base, SparkField field) {
		super(pag, null);
		if (field == null)
			throw new RuntimeException("null field");
		this.base = base;
		this.field = field;
		base.addField(this, field);
		pag.getAllocDotFieldNodeNumberer().add(this);
	}

	public AllocNode getBase() {
		return base;
	}

	public SparkField getField() {
		return field;
	}

	@Override
	public String toString() {
		return "AllocDotField " + getNumber() + " " + base + "." + field;
	}
}