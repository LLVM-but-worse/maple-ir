package org.mapleir.stdlib.collections.graph.dot;

import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public interface  DotPropertyDecorator<G extends FastGraph<N, E>, N extends FastGraphVertex, E extends FastGraphEdge<N>> {
	default DotPropertyDecorator setGraph(G graph) {
		return this;
	}
	
	default void decorateNodeProperties(N n, Map<String, Object> nprops) {
	}
	
	default void decorateEdgeProperties(N n, E e,  Map<String, Object> eprops) {
	}
	
	default void decorateNodePrintability(N n, AtomicBoolean printable) {
	}
	
	default void decorateEdgePrintability(N n, E e, AtomicBoolean printable) {
	}
}
