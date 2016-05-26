package org.rsdeob.stdlib.collections.graph;

import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Stack;

public class DepthFirstSorterImpl<N extends FastGraphVertex> implements Sorter<N> {

	protected DepthFirstSorterImpl() {	
	}
	
	@Override
	public Iterator<N> iterator(FastGraph<N, ?> graph) {
		return new DepthFirstSorterImplIteratorImpl<N>(graph);
	}
	
	private static class DepthFirstSorterImplIteratorImpl<N extends FastGraphVertex> implements Iterator<N> {

		private final FastGraph<N, ?> graph;
		private final Set<N> visited;
		private final Stack<Iterator<N>> stack;
		private N current;
		
		public DepthFirstSorterImplIteratorImpl(FastGraph<N, ?> graph) {
			this.graph = graph;
			stack = new Stack<>();
			visited = new HashSet<>();
			
			stack.push(succs(current = graph.getEntry()));
		}
		
		private Iterator<N> succs(N n) {
			return FastGraph.computeSuccessors(graph, n).iterator();
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