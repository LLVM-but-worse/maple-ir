package org.mapleir.deob.interproc.geompa;

import org.mapleir.deob.interproc.geompa.util.Numberable;
import org.mapleir.ir.TypeCone;

public abstract class PointsToNode implements Numberable {
	protected PAG pag;
	private TypeCone typeCone;
	private int number = 0;
	protected PointsToNode replacement;
	protected AbstractPointsToSet p2set;

	public PointsToNode(PAG pag, TypeCone typeCone) {
		this.pag = pag;
		this.typeCone = typeCone;
		replacement = this;
	}

	public PointsToNode getReplacement() {
		if (replacement != replacement.replacement) {
			replacement = replacement.getReplacement();
		}
		return replacement;
	}

	public void discardP2Set() {
		p2set = null;
	}

	public void setP2Set(AbstractPointsToSet p2set) {
		this.p2set = p2set;
	}

	public AbstractPointsToSet makeP2Set() {
		if (p2set != null) {
			if (replacement != this)
				throw new RuntimeException("Node " + this + " has replacement " + replacement + " but has p2set");
			return p2set;
		}
		PointsToNode rep = getReplacement();
		if (rep == this) {
			p2set = pag.getSetFactory().create(typeCone);
		}
		return rep.makeP2Set();
	}

	public AbstractPointsToSet getP2Set() {
		if (p2set != null) {
			if (replacement != this)
				throw new RuntimeException("Node " + this + " has replacement " + replacement + " but has p2set");
			return p2set;
		}
		PointsToNode rep = getReplacement();
		if (rep == this) {
			return EmptyPointsToSet.INSTANCE;
		}
		return rep.getP2Set();
	}

	public void mergeWith(PointsToNode other) {
		if (other.replacement != other) {
			throw new RuntimeException("Shouldn't happen");
		}
		PointsToNode myRep = getReplacement();
		if (other == myRep)
			return;
		other.replacement = myRep;
		if (other.p2set != p2set && other.p2set != null && !other.p2set.isEmpty()) {
			if (myRep.p2set == null || myRep.p2set.isEmpty()) {
				myRep.p2set = other.p2set;
			} else {
				myRep.p2set.mergeWith(other.p2set);
			}
		}
		other.p2set = null;
		pag.mergedWith(myRep, other);
		if ((other instanceof VarNode) && (myRep instanceof VarNode) && ((VarNode) other).isInterProcTarget()) {
			((VarNode) myRep).setInterProcTarget();
		}
	}

	public TypeCone getTypeCone() {
		return typeCone;
	}

	public void setType(TypeCone typeCone) {
		this.typeCone = typeCone;
	}

	@Override
	public final int getNumber() {
		return number;
	}

	@Override
	public final void setNumber(int number) {
		this.number = number;
	}

	@Override
	public final int hashCode() {
		return number;
	}

	@Override
	public final boolean equals(Object other) {
		return this == other;
	}
}