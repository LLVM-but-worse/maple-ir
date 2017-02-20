package org.mapleir.stdlib.collections.graph.flow;

import org.mapleir.stdlib.collections.graph.FastGraphVertex;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public interface Sorter<N extends FastGraphVertex> {

	default List<N> sort(FlowGraph<N, ?> graph) {
		List<N> list = new ArrayList<>();
		Iterator<N> it = iterator(graph);
		while(it.hasNext()) {
			list.add(it.next());
		}
		return list;
	}
	
	Iterator<N> iterator(FlowGraph<N, ?> graph);
	
	default Iterable<N> iterable(FlowGraph<N, ?> graph) {
		return () -> iterator(graph);
	}
}