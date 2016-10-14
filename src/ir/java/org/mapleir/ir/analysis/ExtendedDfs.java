package org.mapleir.ir.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.ValueCreator;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.graph.flow.FlowGraph;

public class ExtendedDfs<N extends FastGraphVertex> implements DepthFirstSearch<N> {
	
	public static final int WHITE = 0, GREY = 1, BLACK = 2;
	public static final int TREE = WHITE, BACK = GREY, FOR_CROSS = BLACK;
	public static final int EDGES = 0x1, PARENTS = 0x2, PRE = 0x4, POST = 0x8;
	
	private final int opt;
	private final FlowGraph<N, FlowEdge<N>> graph;
	private final NullPermeableHashMap<N, Integer> colours;
	private final Map<Integer, Set<FlowEdge<N>>> edges;
	private final Map<N, N> parents;
	private final List<N> preorder;
	private final List<N> postorder;
	
	public ExtendedDfs(FlowGraph<N, FlowEdge<N>> graph, N entry, int opt) {
		this.opt = opt;
		this.graph = graph;
		colours = new NullPermeableHashMap<>(new ValueCreator<Integer>() {
			@Override
			public Integer create() {
				return WHITE;
			}
		});
		
		parents = opt(PARENTS) ? new HashMap<>() : null;
		preorder = opt(PRE) ? new ArrayList<>() : null;
		postorder = opt(POST) ? new ArrayList<>() : null;
		
		if(opt(EDGES)) {
			edges = new HashMap<>();
			edges.put(TREE, new HashSet<>());
			edges.put(BACK, new HashSet<>());
			edges.put(FOR_CROSS, new HashSet<>());
		} else {
			edges = null;
		}
		
		dfs(entry);
	}
	
	public int getColour(N b) {
		return colours.get(b);
	}
	
	public Map<N, N> getParents() {
		return parents;
	}
	
	public N getParent(N b) {
		return parents.get(b);
	}
	
	public Set<FlowEdge<N>> getEdges(int type) {
		return edges.get(type);
	}

	private boolean opt(int i) {
		return (opt & i) != 0;
	}

	private void dfs(N b) {
		if(opt(PRE)) preorder.add(b);
		colours.put(b, GREY);
		
		for(FlowEdge<N> sE : order(graph.getEdges(b)))  {
			N s = sE.dst;
			if(opt(EDGES)) edges.get(colours.getNonNull(s)).add(sE);
			if(colours.getNonNull(s) == WHITE) {
				if(opt(PARENTS)) parents.put(s, b);
				dfs(s);
			}
		}
		
		if(opt(POST)) postorder.add(b);
		colours.put(b, BLACK);
	}
	
	protected Iterable<FlowEdge<N>> order(Set<FlowEdge<N>> edges) {
		return edges;
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