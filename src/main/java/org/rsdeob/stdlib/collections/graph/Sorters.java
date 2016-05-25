package org.rsdeob.stdlib.collections.graph;

import java.util.HashMap;
import java.util.Map;

public final class Sorters {

	private static final Map<String, Sorter<?>> impls = new HashMap<>();
	
	static {
		impls.put("dfs", new DepthFirstSorterImpl<Object>());
	}
	
	@SuppressWarnings("unchecked")
	public static <N> Sorter<N> get(String id) {
		return (Sorter<N>) impls.get(id);
	}
	
	public static void put(String id, Sorter<?> sorter) {
		impls.put(id, sorter);
	}
}