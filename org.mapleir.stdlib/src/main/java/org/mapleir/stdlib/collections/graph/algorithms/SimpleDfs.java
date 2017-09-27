package org.mapleir.stdlib.collections.graph.algorithms;

import java.util.*;

import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.graph.GraphUtils;

public class SimpleDfs<N extends FastGraphVertex> implements DepthFirstSearch<N> {
	public static final int REVERSE = 1, PRE = 2, POST = 4, TOPO = 8;
	
	private List<N> preorder;
	private List<N> postorder;
	private List<N> topoorder;

	public SimpleDfs(FastDirectedGraph<N, ? extends FastGraphEdge<N>> graph, N entry, int flags) {
		boolean direction = (flags & REVERSE) == 0;
		boolean pre = (flags & PRE) != 0;
		boolean post = (flags & POST) != 0;
		boolean topo = (flags & TOPO) != 0;

		if (pre)
			preorder = new ArrayList<>();
		if (post)
			postorder = new ArrayList<>();
		if (topo)
			topoorder = new ArrayList<>();

		Set<N> visited = new HashSet<>();
		Stack<N> preStack = new Stack<>();
		Stack<N> postStack = null;
		if (post || topo)
			postStack = new Stack<>();

		preStack.push(entry);
		while (!preStack.isEmpty()) {
			N current = preStack.pop();
			if (visited.contains(current))
				continue;
			visited.add(current);
			if (pre)
				preorder.add(current);
			if (post || topo)
				postStack.push(current);
			List<? extends FastGraphEdge<N>> order = GraphUtils.weigh(direction ? graph.getEdges(current) : graph.getReverseEdges(current));
			for (ListIterator<? extends FastGraphEdge<N>> iterator = order.listIterator(order.size()); iterator.hasPrevious(); ) {
				FastGraphEdge<N> succ = iterator.previous();
				preStack.add(direction ? succ.dst() : succ.src());
			}
		}
		if (topo)
			topoorder.addAll(postStack);
		if (post) {
			while (!postStack.isEmpty())
				postorder.add(postStack.pop());
		}
	}

	public static <N extends FastGraphVertex> List<N> preorder(FastDirectedGraph<N, ? extends FastGraphEdge<N>> graph, N entry) {
		return preorder(graph, entry, false);
	}
	
	public static <N extends FastGraphVertex> List<N> preorder(FastDirectedGraph<N, ? extends FastGraphEdge<N>> graph, N entry, boolean reverse) {
		return new SimpleDfs<>(graph, entry, PRE | (reverse? REVERSE : 0)).getPreOrder();
	}
	
	public static <N extends FastGraphVertex> List<N> postorder(FastDirectedGraph<N, ? extends FastGraphEdge<N>> graph, N entry) {
		return postorder(graph, entry, false);
	}
	
	public static <N extends FastGraphVertex> List<N> postorder(FastDirectedGraph<N, ? extends FastGraphEdge<N>> graph, N entry, boolean reverse) {
		return new SimpleDfs<>(graph, entry, POST | (reverse? REVERSE : 0)).getPostOrder();
	}

	public static <N extends FastGraphVertex> List<N> topoorder(FastDirectedGraph<N, ? extends FastGraphEdge<N>> graph, N entry) {
		return topoorder(graph, entry, false);
	}

	public static <N extends FastGraphVertex> List<N> topoorder(FastDirectedGraph<N, ? extends FastGraphEdge<N>> graph, N entry, boolean reverse) {
		return new SimpleDfs<>(graph, entry, TOPO | POST | (reverse? REVERSE : 0)).getTopoOrder();
	}

	@Override
	public List<N> getPreOrder() {
		return preorder;
	}

	@Override
	public List<N> getPostOrder() {
		return postorder;
	}

	@Override
	public List<N> getTopoOrder() {
		return topoorder;
	}
}
