package org.mapleir.deob.interproc.geompa;

import org.objectweb.asm.tree.FieldNode;

public class FieldRefNode extends ValNode {

	protected VarNode base;
	protected FieldNode field;

	FieldRefNode(PAG pag, VarNode base, FieldNode field) {
		super(pag, null);
		if (field == null)
			throw new RuntimeException("null field");
		this.base = base;
		this.field = field;
		base.addField(this, field);
		pag.getFieldRefNodeNumberer().add(this);
	}

	public VarNode getBase() {
		return base;
	}

	@Override
	public PointsToNode getReplacement() {
		if (replacement == this) {
			if (base.replacement == base)
				return this;
			PointsToNode baseRep = base.getReplacement();
			FieldRefNode newRep = pag.makeFieldRefNode((VarNode) baseRep, field);
			newRep.mergeWith(this);
			return replacement = newRep.getReplacement();
		} else {
			return replacement = replacement.getReplacement();
		}
	}

	public FieldNode getField() {
		return field;
	}

	@Override
	public String toString() {
		return "FieldRefNode " + getNumber() + " " + base + "." + field;
	}
}
