package org.mapleir.stdlib.collections.graph;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public interface FastGraph<N extends FastGraphVertex, E extends FastGraphEdge<N>> {

	Set<N> vertices();
	
	void addVertex(N n);
	
	void removeVertex(N n);
	
	boolean containsVertex(N n);
	
	void addEdge(N n, E e);
	
	void removeEdge(N n, E e);
	
	boolean containsEdge(N n, E e);
	
	Set<E> getEdges(N n);
	
	int size();
	
	boolean excavate(N n);
	
	boolean jam(N pred, N succ, N n);
	
	E clone(E edge, N oldN, N newN);
	
	E invert(E edge);
	
	void replace(N old, N n);
	
	void clear();
	
	FastGraph<N, E> copy();
	
	default Map<N, Set<E>> createMap() {
		return new LinkedHashMap<>();
	}
	
	default Map<N, Set<E>> createMap(Map<N, Set<E>> map) {
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
}