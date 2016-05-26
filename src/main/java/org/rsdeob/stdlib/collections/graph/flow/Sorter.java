package org.rsdeob.stdlib.collections.graph.flow;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.rsdeob.stdlib.collections.graph.FastGraphVertex;

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
}