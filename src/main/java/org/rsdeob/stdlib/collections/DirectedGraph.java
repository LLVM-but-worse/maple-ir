package org.rsdeob.stdlib.collections;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class DirectedGraph<V, E> extends HashMap<V, Set<E>> implements Iterable<V> {

	private static final long serialVersionUID = 4647116251285123551L;

	@Override
	public final Iterator<V> iterator() {
		return super.keySet().iterator();
	}

	public final boolean containsVertex(final V vertex) {
		return super.containsKey(vertex);
	}

	public final boolean containsEdge(final V vertex, final E edge) {
		return super.containsKey(vertex) && super.get(vertex).contains(edge);
	}

	public final boolean addVertex(final V vertex) {
		if (super.containsKey(vertex))
			return false;
		super.put(vertex, new HashSet<>());
		return true;
	}

	public final void addEdge(final V vertex, final E edge) {
		if (!super.containsKey(vertex))
			return;
		super.get(vertex).add(edge);
	}

	public final void removeEdge(final V vertex, final E edge) {
		if (!super.containsKey(vertex))
			return;
		super.get(vertex).remove(edge);
	}

	public final Set<E> getEdgesOf(final V vertex) {
		return Collections.unmodifiableSet(super.get(vertex));
	}

	public final void merge(final DirectedGraph<V, E> graph) {
		super.putAll(graph);
	}
}