package org.mapleir.stdlib.collections.graph;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Set;

public class DirectedGraph<V, E> extends LinkedHashMap<V, Set<E>> implements Iterable<V> {

	private static final long serialVersionUID = 4647116251285123551L;
	
	@Override
	public Iterator<V> iterator() {
		return super.keySet().iterator();
	}

	public boolean containsVertex(V vertex) {
		return super.containsKey(vertex);
	}

	public boolean containsEdge(V vertex, E edge) {
		return super.containsKey(vertex) && super.get(vertex).contains(edge);
	}
	
	public boolean addVertex(V vertex) {
		if (super.containsKey(vertex))
			return false;
		super.put(vertex, new HashSet<>());
		return true;
	}

	public void addEdge(V vertex, E edge) {
		if (!super.containsKey(vertex))
			return;
		super.get(vertex).add(edge);
	}

	public void removeEdge(V vertex, E edge) {
		if (!super.containsKey(vertex))
			return;
		super.get(vertex).remove(edge);
	}
	
	public Set<E> getEdgesOf(V vertex) {
		return Collections.unmodifiableSet(super.get(vertex));
	}

	public Set<E> getHashEdgesOf(V vertex) {
		return new HashSet<>(super.get(vertex));
	}
	
	public void merge(DirectedGraph<V, E> graph) {
		super.putAll(graph);
	}
}