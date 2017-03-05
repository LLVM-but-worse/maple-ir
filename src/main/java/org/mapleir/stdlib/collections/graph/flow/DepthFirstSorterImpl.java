package org.mapleir.stdlib.collections.graph.flow;

import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Stack;

import org.mapleir.stdlib.collections.graph.FastGraphVertex;

public class DepthFirstSorterImpl<N extends FastGraphVertex> implements Sorter<N> {

	protected DepthFirstSorterImpl() {	
	}
	
	@Override
	public Iterator<N> iterator(FlowGraph<N, ?> graph) {
		return new DepthFirstSorterImplIteratorImpl<>(graph);
	}
	
	public static class DepthFirstSorterImplIteratorImpl<N extends FastGraphVertex> implements Iterator<N> {

		private final FlowGraph<N, ?> graph;
		private final Set<N> visited;
		private final Stack<Iterator<N>> stack;
		private N current;
		
		public DepthFirstSorterImplIteratorImpl(FlowGraph<N, ?> graph) {
			this.graph = graph;
			stack = new Stack<>();
			visited = new HashSet<>();

			// FIXME: all entries
			stack.push(succs(current = graph.getEntries().iterator().next()));
		}
		
		private Iterator<N> succs(N n) {
			return graph.getEdges(n).stream().map(e -> e.dst).iterator();
		}
		
		@Override
		public boolean hasNext() {
			return current != null;
		}

		private void step() {
			Iterator<N> it = stack.peek();
			do {
				while(!it.hasNext()) {
					stack.pop();
					if(stack.isEmpty()) {
						current = null;
						return;
					}
					it = stack.peek();
				}
				current = it.next();
			} while(visited.contains(current));
			stack.push(succs(current));
		}
		
		@Override
		public N next() {
			if(current == null) {
				throw new NoSuchElementException();
			}
			
			try {
				visited.add(current);
				return current;
			} finally {
				step();
			}
		}
	}
}