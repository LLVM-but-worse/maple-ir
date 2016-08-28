package org.mapleir.stdlib.collections.graph.flow;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.mapleir.stdlib.collections.graph.FastGraphVertex;

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
		return new Iterable<N>() {
			@Override
			public Iterator<N> iterator() {
				return Sorter.this.iterator(graph);
			}
		};
	}
}