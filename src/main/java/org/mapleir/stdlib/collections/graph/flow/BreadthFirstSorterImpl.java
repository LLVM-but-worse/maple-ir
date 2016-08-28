package org.mapleir.stdlib.collections.graph.flow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;

public class BreadthFirstSorterImpl<N extends FastGraphVertex> implements Sorter<N> {

	protected BreadthFirstSorterImpl() {	
	}
	
	@Override
	public Iterator<N> iterator(FlowGraph<N, ?> graph) {
		if(graph.getEntries().size() != 1) {
			throw new UnsupportedOperationException(graph.getEntries().toString());
		} else {
			return bfs(graph, graph.getEntries().iterator().next()).iterator();
		}
	}
	
	public List<N> bfs(FlowGraph<N, ?> graph, N n) {
		LinkedList<N> queue = new LinkedList<>();
		queue.add(n);
		Set<N> visited = new HashSet<>(graph.size());
		
		List<N> bfs = new ArrayList<>();
		while(!queue.isEmpty()) {
			n = queue.pop();
			
			if(visited.contains(n)) {
				continue;
			}
			visited.add(n);
			bfs.add(n);
			
			for(FlowEdge<N> e : graph.getEdges(n)) {
				N s = e.dst;
				queue.addLast(s);
			}
		}
		
		return bfs;
	}
}