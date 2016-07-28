package org.mapleir.stdlib.collections.graph.dot.impl;

import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.graph.dot.DotPropertyDecorator;

import java.util.HashMap;
import java.util.Map;

public class HighlightDecorator<G extends FastGraph<N, E>, N extends FastGraphVertex, E extends FastGraphEdge<N>> implements DotPropertyDecorator<G, N, E> {
	private final Map<N, String> vertexColors;
	
	public HighlightDecorator() {
		vertexColors = new HashMap<>();
	}
	
	public HighlightDecorator<G, N, E> setColor(N n, String color) {
		vertexColors.put(n, color);
		return this;
	}
	
	public void decorateNodeProperties(N n, Map<String, Object> nprops) {
		if (vertexColors.containsKey(n)) {
			nprops.put("style", "filled");
			nprops.put("fillcolor", vertexColors.get(n));
		}
	}
}
