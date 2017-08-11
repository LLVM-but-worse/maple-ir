package org.mapleir.deob.interproc.cxtsenscg;

import org.mapleir.ir.code.Expr;
import org.objectweb.asm.tree.MethodNode;

public interface ContextManager {
	void addStaticEdge(MethodNode src, Expr codeUnit, MethodNode target, Kind kind);

	void addVirtualEdge(MethodNode src, Expr srcUnit, MethodNode target, Kind kind);

	CallGraph callGraph();
}