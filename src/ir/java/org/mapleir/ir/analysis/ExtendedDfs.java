package org.mapleir.ir.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.FastBlockGraph;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.ValueCreator;

public class ExtendedDfs {
	
	public static final int WHITE = 0, GREY = 1, BLACK = 2;
	public static final int TREE = WHITE, BACK = GREY, FOR_CROSS = BLACK;
	public static final int EDGES = 0x1, PARENTS = 0x2, PRE = 0x4, POST = 0x8;
	
	private final int opt;
	private final FastBlockGraph graph;
	private final NullPermeableHashMap<BasicBlock, Integer> colours;
	private final Map<Integer, Set<FlowEdge<BasicBlock>>> edges;
	private final Map<BasicBlock, BasicBlock> parents;
	private final List<BasicBlock> pre;
	private final List<BasicBlock> post;
	
	public ExtendedDfs(FastBlockGraph graph, BasicBlock entry, int opt) {
		this.opt = opt;
		this.graph = graph;
		colours = new NullPermeableHashMap<>(new ValueCreator<Integer>() {
			@Override
			public Integer create() {
				return Integer.valueOf(0);
			}
		});
		
		parents = opt(PARENTS) ? new HashMap<>() : null;
		pre = opt(PRE) ? new ArrayList<>() : null;
		post = opt(POST) ? new ArrayList<>() : null;
		
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
	
	public int getColour(BasicBlock b) {
		return colours.get(b);
	}
	
	public BasicBlock getParent(BasicBlock b) {
		return parents.get(b);
	}
	
	public Set<FlowEdge<BasicBlock>> getEdges(int type) {
		return edges.get(type);
	}
	
	public List<BasicBlock> getPreOrder() {
		return new ArrayList<>(pre);
	}
	
	public List<BasicBlock> getPostOrder() {
		return new ArrayList<>(post);
	}
	
	public List<BasicBlock> getReversePostOrder() {
		List<BasicBlock> list = getPostOrder();
		Collections.reverse(list);
		return list;
	}
	
	private boolean opt(int i) {
		return (opt & i) != 0;
	}

	private void dfs(BasicBlock b) {
		if(opt(PRE)) pre.add(b);
		colours.put(b, GREY);
		
		for(FlowEdge<BasicBlock> sE : graph.getEdges(b))  {
			BasicBlock s = sE.dst;
			if(opt(EDGES)) edges.get(colours.getNonNull(s)).add(sE);
			if(colours.getNonNull(s).intValue() == WHITE) {
				if(opt(PARENTS)) parents.put(s, b);
				dfs(s);
			}
		}
		
		if(opt(POST)) post.add(b);
		colours.put(b, BLACK);
	}
}