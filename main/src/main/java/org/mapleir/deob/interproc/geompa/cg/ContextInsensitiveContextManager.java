package org.mapleir.deob.interproc.geompa.cg;

import org.mapleir.deob.interproc.geompa.Context;
import org.mapleir.deob.interproc.geompa.MapleMethod;
import org.mapleir.deob.interproc.geompa.MapleMethodOrMethodContext;
import org.mapleir.ir.code.CodeUnit;

public class ContextInsensitiveContextManager implements ContextManager {
	private CallGraph cg;

	public ContextInsensitiveContextManager(CallGraph cg) {
		this.cg = cg;
	}

	@Override
	public void addStaticEdge(MapleMethodOrMethodContext src, CodeUnit srcUnit, MapleMethod target, Kind kind) {
		cg.addEdge(new Edge(src, srcUnit, target, kind));
	}

	@Override
	public void addVirtualEdge(MapleMethodOrMethodContext src, CodeUnit srcUnit, MapleMethod target, Kind kind,
			Context typeContext) {
		cg.addEdge(new Edge(src.method(), srcUnit, target, kind));
	}

	@Override
	public CallGraph callGraph() {
		return cg;
	}
}