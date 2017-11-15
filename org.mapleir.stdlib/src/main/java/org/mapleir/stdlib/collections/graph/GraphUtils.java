package org.mapleir.stdlib.collections.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

public class GraphUtils {
	public static final int FAKEHEAD_ID = Integer.MAX_VALUE -1;
	
	public static <N extends FastGraphVertex> List<N> range(List<N> nodes, int start, int end) {
		if(start > end) {
			throw new IllegalArgumentException("start > end: " + start + " > " + end);
		}
		N startNode = null, endNode = null;
		int startIndex = 0, endIndex = 0;
		
		for(int nodeIndex = 0; nodeIndex < nodes.size(); nodeIndex++) {
			N n = nodes.get(nodeIndex);
			
			if(n.getNumericId() == start) {
				startNode = n;
				startIndex = nodeIndex;
			}
			if(n.getNumericId() == end) {
				endNode = n;
				endIndex = nodeIndex;
			}
			
			if(startNode != null && endNode != null) {
				break;
			}
		}
		
		if(startNode == null || endNode == null) {
			throw new UnsupportedOperationException(String.format("start or end null, start=%d, end=%d", start, end));
		} else if(startIndex > endIndex) {
			throw new IllegalArgumentException(String.format("startIndex(%d) > endIndex(%d)", startIndex, endIndex));
		}

		List<N> rangeNodes = new ArrayList<>();
		for(int i=startIndex; i <= endIndex; i++) {
			N n = nodes.get(i);
			if(n == null) {
				throw new IllegalArgumentException(String.format("node id=%d not in range", i));
			}
			rangeNodes.add(n);
		}
		
		return rangeNodes;
	}

	public static <N extends FastGraphVertex> String toNodeArray(Collection<N> col) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		Iterator<N> it = col.iterator();
		while(it.hasNext()) {
			N b = it.next();
			if(b == null) {
				sb.append("null");
			} else {
				sb.append(b.getDisplayName());
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
	
	public static int getEdgeCount(FastGraph<FastGraphVertex, ?> g) {
		int c = 0;		
		for(FastGraphVertex v : g.vertices()) {
			c += g.getEdges(v).size();
		}
		return c;
	}
	
	public static <N extends FastGraphVertex, E extends FastGraphEdge<N> & EdgeCloneable<N, E>> void reverse(
			FastDirectedGraph<N, E> src, FastDirectedGraph<N, E> dst) {
		for (N n : src.vertices()) {
			dst.addVertex(n);

			for (E sE : src.getEdges(n)) {
				/* reverse the edge */
				E newE = sE.clone(sE.dst(), sE.src());
				/* add it as an edge from the succ */
				dst.addEdge(sE.dst(), newE);
			}
		}
	}
}
