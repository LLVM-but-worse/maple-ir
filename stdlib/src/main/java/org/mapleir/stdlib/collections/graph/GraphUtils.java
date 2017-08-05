package org.mapleir.stdlib.collections.graph;


import java.util.*;

public class GraphUtils {
	public static final int FAKEHEAD_ID = Integer.MAX_VALUE -1;

	public static <N extends FastGraphVertex> String toNodeArray(Collection<N> col) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		Iterator<N> it = col.iterator();
		while(it.hasNext()) {
			N b = it.next();
			if(b == null) {
				sb.append("null");
			} else {
				sb.append(b.getId());
			}
			
			if(it.hasNext()) {
				sb.append(", ");
			}
		}
		sb.append("]");
		return sb.toString();
	}

	@SuppressWarnings("unchecked")
	public static <N extends FastGraphVertex, EC extends FastGraphEdge<N> & Comparable<FastGraphEdge<N>>> List<? extends FastGraphEdge<N>> weigh(Set<? extends FastGraphEdge<N>> edges) {
		if (edges.isEmpty())
			return new ArrayList<>();
		if (edges instanceof SortedSet)
			return new ArrayList<>(edges);
		if (Comparable.class.isInstance(edges.iterator().next()))
			return comparableWeigh((Set<EC>) edges);
		else
			return new ArrayList<>(edges);
	}

	private static <N extends FastGraphVertex, E extends FastGraphEdge<N> & Comparable<FastGraphEdge<N>>> List<E> comparableWeigh(Set<E> edges) {
		assert(!(edges instanceof SortedSet));

		List<E> lst = new ArrayList<>();
		if (edges.isEmpty())
			return lst;

		lst.addAll(edges);
		Collections.sort(lst);

		return lst;
	}
}
