package org.mapleir.stdlib.collections.graph.dot;

import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public interface  DotPropertyDecorator<G extends FastGraph<N, E>, N extends FastGraphVertex, E extends FastGraphEdge<N>> {
	default void decorateNodeProperties(G g, N n, Map<String, Object> nprops) {
	}
	
	default void decorateEdgeProperties(G g, N n, E e,  Map<String, Object> eprops) {
	}
	
	default void decorateNodePrintability(G g, N n, AtomicBoolean printable) {
	}
	
	default void decorateEdgePrintability(G g, N n, E e, AtomicBoolean printable) {
	}
}
