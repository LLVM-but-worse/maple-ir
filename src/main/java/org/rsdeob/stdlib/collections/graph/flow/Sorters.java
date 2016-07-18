package org.rsdeob.stdlib.collections.graph.flow;

import org.rsdeob.stdlib.collections.graph.FastGraphVertex;

import java.util.HashMap;
import java.util.Map;

public final class Sorters {

	private static final Map<String, Sorter<?>> impls = new HashMap<>();
	
	static {
		impls.put("dfs", new DepthFirstSorterImpl<FastGraphVertex>());
	}
	
	@SuppressWarnings("unchecked")
	public static <N extends FastGraphVertex> Sorter<N> get(String id) {
		return (Sorter<N>) impls.get(id);
	}
	
	public static void put(String id, Sorter<?> sorter) {
		impls.put(id, sorter);
	}
}