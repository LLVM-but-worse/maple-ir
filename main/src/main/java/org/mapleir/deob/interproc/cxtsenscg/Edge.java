package org.mapleir.deob.interproc.cxtsenscg;

import org.mapleir.ir.code.Expr;
import org.objectweb.asm.tree.MethodNode;

public final class Edge {
	private MethodNode src;
	private Expr srcUnit;
	private Kind kind;
	private MethodNode tgt;
	
	private Edge nextByUnit = this;
	private Edge prevByUnit = this;
	private Edge nextBySrc = this;
	private Edge prevBySrc = this;
	private Edge nextByTgt = this;
	private Edge prevByTgt = this;

	public Edge(MethodNode src, Expr srcUnit, MethodNode tgt, Kind kind) {
		this.src = src;
		this.srcUnit = srcUnit;
		this.tgt = tgt;
		this.kind = kind;
	}
	
	public MethodNode src() {
		return src;
	}

	public Expr srcUnit() {
		return srcUnit;
	}

	public MethodNode tgt() {
		return tgt;
	}

	public Kind kind() {
		return kind;
	}
	
	public boolean isExplicit() {
		return kind.isExplicit();
	}

	public boolean isInstance() {
		return kind.isInstance();
	}

	public boolean isVirtual() {
		return kind.isVirtual();
	}

	public boolean isSpecial() {
		return kind.isSpecial();
	}

	public boolean isClinit() {
		return kind.isClinit();
	}

	public boolean isStatic() {
		return kind.isStatic();
	}

	public boolean isThreadRunCall() {
		return kind.isThread();
	}

	public boolean passesParameters() {
		return kind.passesParameters();
	}

	Edge nextByUnit() {
		return nextByUnit;
	}

	Edge nextBySrc() {
		return nextBySrc;
	}

	Edge nextByTgt() {
		return nextByTgt;
	}

	Edge prevByUnit() {
		return prevByUnit;
	}

	Edge prevBySrc() {
		return prevBySrc;
	}

	Edge prevByTgt() {
		return prevByTgt;
	}	
	
	void insertAfterByUnit(Edge other) {
		nextByUnit = other.nextByUnit;
		nextByUnit.prevByUnit = this;
		other.nextByUnit = this;
		prevByUnit = other;
	}

	void insertAfterBySrc(Edge other) {
		nextBySrc = other.nextBySrc;
		nextBySrc.prevBySrc = this;
		other.nextBySrc = this;
		prevBySrc = other;
	}

	void insertAfterByTgt(Edge other) {
		nextByTgt = other.nextByTgt;
		nextByTgt.prevByTgt = this;
		other.nextByTgt = this;
		prevByTgt = other;
	}

	void insertBeforeByUnit(Edge other) {
		prevByUnit = other.prevByUnit;
		prevByUnit.nextByUnit = this;
		other.prevByUnit = this;
		nextByUnit = other;
	}

	void insertBeforeBySrc(Edge other) {
		prevBySrc = other.prevBySrc;
		prevBySrc.nextBySrc = this;
		other.prevBySrc = this;
		nextBySrc = other;
	}

	void insertBeforeByTgt(Edge other) {
		prevByTgt = other.prevByTgt;
		prevByTgt.nextByTgt = this;
		other.prevByTgt = this;
		nextByTgt = other;
	}

	void remove() {
		nextByUnit.prevByUnit = prevByUnit;
		prevByUnit.nextByUnit = nextByUnit;
		nextBySrc.prevBySrc = prevBySrc;
		prevBySrc.nextBySrc = nextBySrc;
		nextByTgt.prevByTgt = prevByTgt;
		prevByTgt.nextByTgt = nextByTgt;
	}

	@Override
	public int hashCode() {
		int ret = tgt.hashCode() + kind.getNumber();
		if (src != null)
			ret += src.hashCode();
		if (srcUnit != null)
			ret += srcUnit.hashCode();
		return ret;
	}

	@Override
	public boolean equals(Object other) {
		Edge o = (Edge) other;
		if (o == null)
			return false;
		if (o.src != src)
			return false;
		if (o.srcUnit != srcUnit)
			return false;
		if (o.tgt != tgt)
			return false;
		if (o.kind != kind)
			return false;
		return true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		sb.append("Edge").append("(").append(kind).append(")::").append(System.lineSeparator());
		sb.append("  ").append("srcUnit: ").append(srcUnit).append(System.lineSeparator());
		sb.append("  ").append("src: ").append(src).append(System.lineSeparator());
		sb.append("  ").append("tgt: ").append(tgt);

		return sb.toString();
	}
}
