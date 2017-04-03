package org.mapleir.stdlib.util.dot;

import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;

import java.util.Map;

public interface  DotPropertyDecorator<G extends FastGraph<N, E>, N extends FastGraphVertex, E extends FastGraphEdge<N>> {
	
	default void decorateNodeProperties(G g, N n, Map<String, Object> nprops) {
	}
	
	default void decorateEdgeProperties(G g, N n, E e,  Map<String, Object> eprops) {
	}
	
	default boolean isNodePrintable(G g, N n) {
		return true;
	}
	
	default boolean isEdgePrintable(G g, N n, E e) {
		return true;
	}
}
