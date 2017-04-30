package org.mapleir.deob.interproc.builder;

import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraph;

public class ContextInsensitiveCallGraph<T extends CallGraphNode> extends FastDirectedGraph<T, ContextInsensitiveInvocation<T>> {
	
	@Override
	public boolean excavate(T n) {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public boolean jam(T pred, T succ, T n) {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public ContextInsensitiveInvocation<T> clone(ContextInsensitiveInvocation<T> edge, T oldN, T newN) {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public ContextInsensitiveInvocation<T> invert(ContextInsensitiveInvocation<T> edge) {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public FastGraph<T, ContextInsensitiveInvocation<T>> copy() {
		throw new UnsupportedOperationException("TODO");
	}
}