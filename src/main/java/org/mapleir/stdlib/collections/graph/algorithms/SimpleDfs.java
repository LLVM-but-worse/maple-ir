package org.mapleir.stdlib.collections.graph.algorithms;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.mapleir.ir.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.graph.GraphUtils;
import org.mapleir.stdlib.collections.graph.flow.FlowGraph;

public class SimpleDfs<N extends FastGraphVertex> implements DepthFirstSearch<N> {
	
	private List<N> preorder;
	private List<N> postorder;

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
			for (FlowEdge<N> succ : GraphUtils.weigh(graph.getEdges(current)))
				preStack.push(succ.dst);
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
