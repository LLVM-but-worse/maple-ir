package org.mapleir.deob.interproc.cxtsenscg;

import org.mapleir.ir.code.Expr;
import org.objectweb.asm.tree.MethodNode;

public class ContextInsensitiveContextManager implements ContextManager {
	private CallGraph cg;

	public ContextInsensitiveContextManager(CallGraph cg) {
		this.cg = cg;
	}

	@Override
	public void addStaticEdge(MethodNode src, Expr srcUnit, MethodNode target, Kind kind) {
		cg.addEdge(new Edge(src, srcUnit, target, kind));
	}

	@Override
	public void addVirtualEdge(MethodNode src, Expr srcUnit, MethodNode target, Kind kind) {
		cg.addEdge(new Edge(src, srcUnit, target, kind));
	}

	@Override
	public CallGraph callGraph() {
		return cg;
	}
}