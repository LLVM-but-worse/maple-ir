package org.mapleir.stdlib.collections.graph.dot.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.edge.TryCatchEdge;
import org.mapleir.stdlib.collections.graph.dot.DotConfiguration;
import org.mapleir.stdlib.collections.graph.dot.DotWriter;
import org.mapleir.stdlib.collections.graph.util.GraphUtils;

public class ControlFlowGraphDotWriter extends DotWriter<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> {

	public static final int OPT_DEEP = 0x01;
	public static final int OPT_HIDE_HANDLER_EDGES = 0x02;
	
	private int flags;
	private List<BasicBlock> order;
	
	public ControlFlowGraphDotWriter(DotConfiguration<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> config, ControlFlowGraph graph) {
		super(config, graph);
		flags = 0;
		order = new ArrayList<>();
	}
	
	public int getFlags() {
		return flags;
	}
	
	public ControlFlowGraphDotWriter setFlags(int flags) {
		this.flags = flags;
		return this;
	}
	
	public List<BasicBlock> getOrder() {
		return order;
	}
	
	public ControlFlowGraphDotWriter setOrder(List<BasicBlock> order) {
		if(order != null) {
			this.order = order;
		}
		return this;
	}
	
	@Override
	protected boolean printable(BasicBlock n) {
		return !n.isDummy();
	}
	
	@Override
	protected boolean printable(BasicBlock n, FlowEdge<BasicBlock> e) {
		return !((flags & OPT_HIDE_HANDLER_EDGES) != 0 && e instanceof TryCatchEdge);
	}
	
	@Override
	public Map<String, Object> getNodeProperties(BasicBlock n) {
		Map<String, Object> map = new HashMap<>(8);
		map.put("shape", "box");
		map.put("labeljust", "l");
		
		if(graph.getEntries().contains(n)) {
			map.put("style", "filled");
			map.put("fillcolor", "red");
		} else if(graph.getEdges(n).size() == 0) {
			map.put("style", "filled");
			map.put("fillcolor", "green");
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append(order.indexOf(n)).append(". ").append(n.getId());
		if((flags & OPT_DEEP) != 0) {
			sb.append("\\l");
			StringBuilder sb2 = new StringBuilder();
			GraphUtils.printBlock(graph, sb2, n, 0, false, false);
			sb.append(sb2.toString().replace("\n", "\\l"));
		}
		map.put("label", sb.toString());
		
		return map;
	}
	
	@Override
	public Map<String, Object> getEdgeProperties(BasicBlock n, FlowEdge<BasicBlock> e) {
		if((flags & OPT_DEEP) != 0) {
			Map<String, Object> map = new HashMap<>(1);
			map.put("label", e.toGraphString());
			return map;
		} else {
			return null;
		}
	}
}