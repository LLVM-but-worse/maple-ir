package org.mapleir.ir.analysis;

import java.util.*;

import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.edge.FlowEdges;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.graph.flow.FlowGraph;

public class SimpleDfs<N extends FastGraphVertex> {
	public List<N> preorder;
	public List<N> postorder;

	public SimpleDfs(FlowGraph<N, FlowEdge<N>> graph, N entry, boolean pre, boolean post) {
		if (pre)
			preorder = new ArrayList<>();
		if (post)
			postorder = new ArrayList<>();

		Set<N> visited = new HashSet<>();
		Stack<N> preStack = new Stack<>();
		Stack<N> postStack = null;
		if (post)
			postStack = new Stack<>();

		preStack.push(entry);
		while (!preStack.isEmpty()) {
			N current = preStack.pop();
			if (visited.contains(current))
				continue;
			visited.add(current);
			if (pre)
				preorder.add(current);
			if (post)
				postStack.push(current);
			for (FlowEdge<N> succ : weigh(graph.getEdges(current)))
				preStack.push(succ.dst);
		}
		if (post)
			while (!postStack.isEmpty())
				postorder.add(postStack.pop());
	}

	private Collection<FlowEdge<N>> weigh(Set<FlowEdge<N>> edges) {
		List<FlowEdge<N>> lst = new ArrayList<>();
		lst.addAll(edges);
		Collections.sort(lst, new Comparator<FlowEdge<N>>() {
			@Override
			public int compare(FlowEdge<N> o1, FlowEdge<N> o2) {
				if(o1.getType() == FlowEdges.DUMMY) {
					return 1;
				} else if(o2.getType() == FlowEdges.DUMMY) {
					return -1;
				}
				return 0;
			}
		});
		return lst;
	}
}
