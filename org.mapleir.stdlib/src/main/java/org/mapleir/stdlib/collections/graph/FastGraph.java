package org.mapleir.stdlib.collections.graph;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.stdlib.collections.graph.dot.DotConfiguration;
import org.mapleir.stdlib.collections.graph.dot.DotWriter;

public interface FastGraph<N extends FastGraphVertex, E extends FastGraphEdge<N>> {

	Set<N> vertices();
	
	boolean addVertex(N n);
	
	void removeVertex(N n);
	
	boolean containsVertex(N n);
	
	void addEdge(N n, E e);
	
	void removeEdge(N n, E e);
	
	boolean containsEdge(N n, E e);
	
	Set<E> getEdges(N n);
	
	int size();
	
	default boolean excavate(N n) {
		throw new UnsupportedOperationException();
	}
	
	default boolean jam(N pred, N succ, N n) {
		throw new UnsupportedOperationException();
	}
	
	default E clone(E edge, N oldN, N newN) {
		throw new UnsupportedOperationException();
	}
	
	default E invert(E edge) {
		throw new UnsupportedOperationException();
	}
	
	void replace(N old, N n);
	
	void clear();
	
	default FastGraph<N, E> copy() {
		throw new UnsupportedOperationException();
	}
	
	// FastGraph<N, E> inducedSubgraph(Collection<N> vertices);
	
	default Map<N, Set<E>> createMap() {
		return new LinkedHashMap<>();
	}
	
	default Map<N, Set<E>> createMap(Map<N, Set<E>> map) {
		// WHY IS IT IMPORTANT TO BE SORTED????
		LinkedHashMap<N, Set<E>> map2 = new LinkedHashMap<>();
		for(Entry<N, Set<E>> e : map.entrySet()) {
			map2.put(e.getKey(), createSet(e.getValue()));
		}
		return map2;
	}
	
	default Set<E> createSet() {
		return new HashSet<>();
	}
	
	default Set<E> createSet(Set<E> set) {
		return new HashSet<>(set);
	}
	
	DotConfiguration<FastGraph<N,E>, N, E> makeDotConfiguration();

	default DotWriter<FastGraph<N,E>, N, E> makeDotWriter() {
		return new DotWriter<>(makeDotConfiguration(), this);
	}
}
