package org.mapleir.stdlib.collections.graph.algorithms;

import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.nullpermeable.KeyedValueCreator;
import org.mapleir.stdlib.collections.nullpermeable.NullPermeableHashMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExtendedDfs<N extends FastGraphVertex> implements DepthFirstSearch<N> {
	
	public static final int WHITE = 0, GREY = 1, BLACK = 2;
	public static final int TREE = WHITE, BACK = GREY, FOR_CROSS = BLACK;
	public static final int EDGES = 0x1, PARENTS = 0x2, PRE = 0x4, POST = 0x8, REVERSE = 0x10, 
							COLOUR_VISITOR = 0x20;
	
	private final int opt;
	private final FastDirectedGraph<N, ? extends FastGraphEdge<N>> graph;
	private final NullPermeableHashMap<N, Integer> colours;
	private final Map<Integer, Set<FastGraphEdge<N>>> edges;
	private final Map<N, N> parents;
	private final List<N> preorder;
	private final List<N> postorder;
	
	public ExtendedDfs(FastDirectedGraph<N, ? extends FastGraphEdge<N>> graph, int opt) {
		this.opt = opt;
		this.graph = graph;
		colours = new NullPermeableHashMap<>(new KeyedValueCreator<N, Integer>() {
			@Override
			public Integer create(N k) {
				if(opt(COLOUR_VISITOR)) coloured(k, WHITE);
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
	}
	
	public ExtendedDfs<N> run(N entry) {
		dfs(null, entry);
		return this;
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
	
	public Set<FastGraphEdge<N>> getEdges(int type) {
		return edges.get(type);
	}

	private boolean opt(int i) {
		return (opt & i) != 0;
	}

	protected void dfs(N par, N b) {
		boolean cvisit = opt(COLOUR_VISITOR);
		boolean reverse = opt(REVERSE);
		
		if(opt(PRE)) preorder.add(b);
		
		colours.put(b, GREY);
		if(cvisit) coloured(b, GREY);
		
		for(FastGraphEdge<N> sE : order(reverse ? graph.getReverseEdges(b) : graph.getEdges(b)))  {
			N s = reverse ? sE.src : sE.dst;
			if(opt(EDGES)) edges.get(colours.getNonNull(s)).add(sE);
			
			if(colours.getNonNull(s) == WHITE) {
				if(opt(PARENTS)) parents.put(s, b);
				dfs(b, s);
			}
		}
		
		if(opt(POST)) postorder.add(b);
		
		colours.put(b, BLACK);
		if(cvisit) coloured(b, BLACK);
	}
	
	protected void coloured(N n, int c) {
	}
	
	protected Iterable<? extends FastGraphEdge<N>> order(Set<? extends FastGraphEdge<N>> edges) {
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