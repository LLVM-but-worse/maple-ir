package org.mapleir.deob.interproc.cxtsenscg;

import org.mapleir.ir.code.expr.invoke.Invocation;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.objectweb.asm.tree.MethodNode;

public class CallEdge implements FastGraphEdge<MethodNode> {
	public final MethodNode src, dst;
	public final Invocation invoke;
	public final int type;
	
	public CallEdge(MethodNode src, MethodNode dst, Invocation invoke, int type) {
		this.src = src;
		this.dst = dst;
		this.invoke = invoke;
		this.type = type;
	}

	@Override
	public MethodNode src() {
		return src;
	}

	@Override
	public MethodNode dst() {
		return dst;
	}
}