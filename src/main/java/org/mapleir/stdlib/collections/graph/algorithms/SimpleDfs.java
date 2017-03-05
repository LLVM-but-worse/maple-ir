package org.mapleir.stdlib.collections.graph.algorithms;

import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.graph.GraphUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

public class SimpleDfs<N extends FastGraphVertex> implements DepthFirstSearch<N> {
	public static final int REVERSE = 1, PRE = 2, POST = 4;
	
	private List<N> preorder;
	private List<N> postorder;
	
	public SimpleDfs(FastDirectedGraph<N, ? extends FastGraphEdge<N>> graph, N entry, int flags) {
		boolean direction = (flags & REVERSE) == 0;
		boolean pre = (flags & PRE) != 0;
		boolean post = (flags & POST) != 0;
		
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
			for (FastGraphEdge<N> succ : GraphUtils.weigh(direction ? graph.getEdges(current) : graph.getReverseEdges(current)))
				preStack.push(direction? succ.dst : succ.src);
		}
		if (post)
			while (!postStack.isEmpty())
				postorder.add(postStack.pop());
	}

	@Override
	public List<N> getPreOrder() {
		return preorder;
	}

	@Override
	public List<N> getPostOrder() {
		return postorder;
	}
}
