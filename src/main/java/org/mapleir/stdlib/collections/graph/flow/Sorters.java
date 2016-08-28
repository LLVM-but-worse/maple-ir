package org.mapleir.stdlib.collections.graph.flow;

import java.util.HashMap;
import java.util.Map;

import org.mapleir.stdlib.collections.graph.FastGraphVertex;

public final class Sorters {

	private static final Map<String, Sorter<?>> impls = new HashMap<>();
	
	static {
		impls.put("dfs", new DepthFirstSorterImpl<>());
		impls.put("bfs", new BreadthFirstSorterImpl<>());
	}
	
	@SuppressWarnings("unchecked")
	public static <N extends FastGraphVertex> Sorter<N> get(String id) {
		return (Sorter<N>) impls.get(id);
	}
	
	public static void put(String id, Sorter<?> sorter) {
		impls.put(id, sorter);
	}
}