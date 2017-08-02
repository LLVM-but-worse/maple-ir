package org.mapleir.deob.interproc.geompa.cg;

import org.mapleir.deob.interproc.geompa.Context;
import org.mapleir.deob.interproc.geompa.MapleMethod;
import org.mapleir.deob.interproc.geompa.MapleMethodOrMethodContext;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.invoke.InitialisedObjectExpr;
import org.mapleir.ir.code.expr.invoke.Invocation;
import org.mapleir.ir.code.expr.invoke.InvocationExpr;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.Printer;

public final class Edge {
	/**
	 * The method in which the call occurs; may be null for calls not occurring in a specific method (eg. implicit calls by the VM)
	 */
	private MapleMethodOrMethodContext src;
	/**
	 * The unit at which the call occurs; may be null for calls not occurring at a specific statement (eg. calls in native code)
	 */
	private CodeUnit srcUnit;
	/** The target method of the call edge. */
	private MapleMethodOrMethodContext tgt;
	/**
	 * The kind of edge. Note: kind should not be tested by other classes; instead, accessors such as isExplicit() should be added.
	 **/
	private Kind kind;
	
	private Edge nextByUnit = this;
	private Edge prevByUnit = this;
	private Edge nextBySrc = this;
	private Edge prevBySrc = this;
	private Edge nextByTgt = this;
	private Edge prevByTgt = this;

	public Edge(MapleMethodOrMethodContext src, CodeUnit srcUnit, MapleMethodOrMethodContext tgt, Kind kind) {
		this.src = src;
		this.srcUnit = srcUnit;
		this.tgt = tgt;
		this.kind = kind;
	}

	public Edge(MapleMethodOrMethodContext src, Invocation srcUnit, MapleMethodOrMethodContext tgt) {
		kind = ieToKind(srcUnit);
		this.src = src;
		this.srcUnit = srcUnit;
		this.tgt = tgt;
	}

	public static Kind ieToKind(Invocation e) {
		if(e instanceof InvocationExpr) {
			InvocationExpr ie = (InvocationExpr) e;
			
			if(ie.getCallType() == Opcodes.INVOKESTATIC) {
				return Kind.STATIC;
			} else if(ie.getCallType() == Opcodes.INVOKEINTERFACE) {
//				return Kind.INTERFACE;
				throw new UnsupportedOperationException();
			} else if(ie.getCallType() == Opcodes.INVOKESPECIAL) {
				return Kind.SPECIAL;
			} else if(ie.getCallType() == Opcodes.INVOKESTATIC) {
				return Kind.STATIC;
			} else {
				throw new RuntimeException(Printer.OPCODES[ie.getCallType()]);
			}
		} else if(e instanceof InitialisedObjectExpr) {
			return Kind.SPECIAL;
		} else {
			throw new RuntimeException();
		}
	}

	public MapleMethod src() {
		if (src == null)
			return null;
		else
			return src.method();
	}

	public Context srcCtxt() {
		if (src == null)
			return null;
		else
			return src.context();
	}

	public MapleMethodOrMethodContext getSrc() {
		return src;
	}

	public CodeUnit srcUnit() {
		return srcUnit;
	}

	public Stmt srcStmt() {
		return (Stmt)srcUnit;
	}


	public MapleMethod tgt() {
		return tgt.method();
	}

	public Context tgtCtxt() {
		return tgt.context();
	}

	public MapleMethodOrMethodContext getTgt() {
		return tgt;
	}


	public Kind kind() {
		return kind;
	}

	/** Returns true if the call is due to an explicit invoke statement. */
	public boolean isExplicit() {
		return kind.isExplicit();
	}

	/**
	 * Returns true if the call is due to an explicit instance invoke statement.
	 */
	public boolean isInstance() {
		return kind.isInstance();
	}

	public boolean isVirtual() {
		return kind.isVirtual();
	}

	public boolean isSpecial() {
		return kind.isSpecial();
	}

	/** Returns true if the call is to static initializer. */
	public boolean isClinit() {
		return kind.isClinit();
	}

	/**
	 * Returns true if the call is due to an explicit static invoke statement.
	 */
	public boolean isStatic() {
		return kind.isStatic();
	}

	public boolean isThreadRunCall() {
		return kind.isThread();
	}

	public boolean passesParameters() {
		return kind.passesParameters();
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
		return kind.toString() + " edge: " + srcUnit + " in " + src + " ==> " + tgt;
	}
}