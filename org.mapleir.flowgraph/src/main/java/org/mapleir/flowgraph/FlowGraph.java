package org.mapleir.flowgraph;

import org.mapleir.flowgraph.edges.FlowEdge;
import org.mapleir.flowgraph.edges.TryCatchEdge;
import org.mapleir.stdlib.collections.bitset.BitSetIndexer;
import org.mapleir.stdlib.collections.bitset.GenericBitSet;
import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.map.ValueCreator;

import java.util.*;

public abstract class FlowGraph<N extends FastGraphVertex, E extends FlowEdge<N>> extends FastDirectedGraph<N, E> implements ValueCreator<GenericBitSet<N>> {
	
	protected final List<ExceptionRange<N>> ranges;
	protected final Set<N> entries;
	
	protected final BitSetIndexer<N> indexer;
	protected final Map<Integer, N> indexMap;
	protected final BitSet indexedSet;
	
	public FlowGraph() {
		ranges = new ArrayList<>();
		entries = new HashSet<>();

		indexer = new FastGraphVertexBitSetIndexer();
		indexMap = new HashMap<>();
		indexedSet = new BitSet();
	}
	
	public FlowGraph(FlowGraph<N, E> g) {
		super(g);
		
		ranges = new ArrayList<>(g.ranges);
		entries = new HashSet<>(g.entries);

		indexer = g.indexer;
		indexMap = new HashMap<>(g.indexMap);
		indexedSet = g.indexedSet;
	}
	
	public Set<N> getEntries() {
		return entries;
	}
	
	public void addRange(ExceptionRange<N> range) {
		if(!ranges.contains(range)) {
			ranges.add(range);
		}
	}
	
	public void removeRange(ExceptionRange<N> range) {
		ranges.remove(range);
	}
	
	public List<ExceptionRange<N>> getRanges() {
		return new ArrayList<>(ranges);
	}
	
	@Override
	public void clear() {
		super.clear();
		indexMap.clear();
		indexedSet.clear();
	}
	
	@Override
	public boolean addVertex(N v) {
		boolean ret = super.addVertex(v);
		
		int index = v.getNumericId();
		indexMap.put(index, v);
		indexedSet.set(index, true);
		return ret;
	}	
	
	@Override
	public void addEdge(E e) {
		super.addEdge(e);

		N src = e.src();
		int index = src.getNumericId();
		indexMap.put(index, src);
		indexedSet.set(index, true);
	}
	
	@Override
	public void replace(N old, N n) {
		if(entries.contains(old)) {
			entries.add(n);
		}
		super.replace(old, n);
	}
	
	@Override
	public void removeVertex(N v) {
		ListIterator<ExceptionRange<N>> it = ranges.listIterator();
		while(it.hasNext()) {
			ExceptionRange<N> r = it.next();
			if (r.containsVertex(v)) {
				r.removeVertex(v);
				if (r.get().isEmpty()) {
					it.remove();
				}
			}
		}
		
		entries.remove(v);
		super.removeVertex(v);

		int index = v.getNumericId();
		indexMap.remove(index);
		indexedSet.set(index, false);
	}
	
	public Set<N> wanderAllTrails(N from, N to, boolean forward) {
		return wanderAllTrails(from, to, forward, true);
	}

	public Set<N> wanderAllTrails(N from, N to) {
		return wanderAllTrails(from, to, true, false);
	}

	public Set<N> wanderAllTrails(N from, N to, boolean forward, boolean followExceptions) {
		Set<N> visited = new HashSet<>();
		LinkedList<N> stack = new LinkedList<>();
		stack.add(from);
		
		while(!stack.isEmpty()) {
			N s = stack.pop();
			
			Set<E> edges = forward ? getEdges(s) : getReverseEdges(s);
			for(FlowEdge<N> e : edges) {
				if(e instanceof TryCatchEdge && !followExceptions)
					continue;
				N next = forward ? e.dst() : e.src();
				if(next != to && !visited.contains(next)) {
					stack.add(next);
					visited.add(next);
				}
			}
		}
		
		visited.add(from);
		
		return visited;
	}	
	
	public Set<N> wanderAllTrails(N from) {
		Set<N> visited = new HashSet<>();
		LinkedList<N> stack = new LinkedList<>();
		stack.add(from);
		
		while(!stack.isEmpty()) {
			N s = stack.pop();
			
			Set<E> edges = getEdges(s);
			for(FlowEdge<N> e : edges) {
				N next = e.dst();
				if(!visited.contains(next)) {
					stack.add(next);
					visited.add(next);
				}
			}
		}
		
		visited.add(from);
		
		return visited;
	}

	public GenericBitSet<N> createBitSet() {
		return new GenericBitSet<>(indexer);
	}

	@Override
	public GenericBitSet<N> create() {
		return createBitSet();
	}

	private class FastGraphVertexBitSetIndexer implements BitSetIndexer<N> {
		@Override
		public int getIndex(N basicBlock) {
			return basicBlock.getNumericId();
		}

		@Override
		public N get(int index) {
			return indexMap.get(index);
		}

		@Override
		public boolean isIndexed(N basicBlock) {
			return basicBlock != null && indexedSet.get(getIndex(basicBlock));
		}
	}
}
