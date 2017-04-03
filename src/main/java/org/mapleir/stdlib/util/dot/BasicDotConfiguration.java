package org.mapleir.stdlib.util.dot;

import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;

import java.util.HashMap;
import java.util.Map;

public class BasicDotConfiguration<G extends FastGraph<N, E>, N extends FastGraphVertex, E extends FastGraphEdge<N>> extends DotConfiguration<G, N, E> {

	public BasicDotConfiguration(GraphType type) {
		super(type);
		
		Map<String, Object> props = new HashMap<>();
		props.put("fontname", "consolas bold");
		props.put("fontsize", 8.0D);
		props.put("dpi", 200.0D);
		
		for(String p : BASIC_GRAPH_PROPERTIES) {
			addGlobalProperties(p, props);
		}
	}
}