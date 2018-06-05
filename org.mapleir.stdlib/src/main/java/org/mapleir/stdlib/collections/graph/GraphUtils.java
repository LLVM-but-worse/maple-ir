package org.mapleir.stdlib.collections.graph;

import java.util.Collection;
import java.util.Iterator;

public class GraphUtils {
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

	public static int getEdgeCount(FastGraph<FastGraphVertex, ?> g) {
		int c = 0;		
		for(FastGraphVertex v : g.vertices()) {
			c += g.getEdges(v).size();
		}
		return c;
	}

	@Deprecated
	public static <N extends FastGraphVertex, E extends FastGraphEdge<N>> int compareEdgesById(E a, E b) {
		if (a.equals(b))
			return 0;
		else {
			int result = Integer.compare(a.src().getNumericId(), b.src().getNumericId());
			if (result == 0)
				result = Integer.compare(a.dst().getNumericId(), b.dst().getNumericId());
			assert (result != 0);
			return result;
		}
	}
}
