package org.mapleir.deob.interproc.builder;

import org.mapleir.stdlib.collections.graph.FastGraphEdge;

public class ContextInsensitiveInvocation<T extends CallGraphNode> extends FastGraphEdge<T> {
	public ContextInsensitiveInvocation(T src, T dst) {
		super(src, dst);
	}
}