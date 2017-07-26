package org.mapleir.stdlib.collections.graph;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

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
	
	public static <N extends FastGraphVertex> List<? extends FastGraphEdge<N>> weigh(Set<? extends FastGraphEdge<N>> edges) {
		List<FastGraphEdge<N>> lst = new ArrayList<>();
		if (edges.isEmpty())
			return lst;
		
		lst.addAll(edges);
		Collections.sort(lst);
		
		// Moved to FastGraphEdge.compareTo
		/*if (lst.get(0) instanceof FlowEdge) {
			((List<? extends FlowEdge<N>>) lst).sort(new Comparator<FlowEdge<N>>() {
				@Override
				public int compare(FlowEdge<N> o1, FlowEdge<N> o2) {
					if (o1.getType() == FlowEdges.DUMMY) {
						return 1;
					} else if (o2.getType() == FlowEdges.DUMMY) {
						return -1;
					}
					return 0;
				}
			});
		}*/
		
		return lst;
	}
}