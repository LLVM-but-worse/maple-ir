package org.mapleir.deob.interproc.geompa.cg;

import org.mapleir.deob.interproc.geompa.Context;
import org.mapleir.deob.interproc.geompa.MapleMethod;
import org.mapleir.deob.interproc.geompa.MapleMethodOrMethodContext;
import org.mapleir.ir.code.expr.invoke.Invocation;

public interface ContextManager {
	void addStaticEdge(MapleMethodOrMethodContext src, Invocation srcUnit, MapleMethod target, Kind kind);

	void addVirtualEdge(MapleMethodOrMethodContext src, Invocation srcUnit, MapleMethod target, Kind kind,
			Context typeContext);

	CallGraph callGraph();
}