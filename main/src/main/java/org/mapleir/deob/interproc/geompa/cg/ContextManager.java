package org.mapleir.deob.interproc.geompa.cg;

import org.mapleir.deob.interproc.geompa.Context;
import org.mapleir.deob.interproc.geompa.MapleMethod;
import org.mapleir.deob.interproc.geompa.MapleMethodOrMethodContext;
import org.mapleir.ir.code.CodeUnit;

public interface ContextManager {
	void addStaticEdge(MapleMethodOrMethodContext src, CodeUnit codeUnit, MapleMethod target, Kind kind);

	void addVirtualEdge(MapleMethodOrMethodContext src, CodeUnit srcUnit, MapleMethod target, Kind kind,
			Context typeContext);

	CallGraph callGraph();
}