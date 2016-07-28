package org.mapleir.stdlib.collections.graph.dot.impl;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.edge.TryCatchEdge;
import org.mapleir.stdlib.collections.graph.dot.DotPropertyDecorator;
import org.mapleir.stdlib.collections.graph.util.GraphUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ControlFlowGraphDecorator implements DotPropertyDecorator<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> {
	public static final int OPT_DEEP = 0x01;
	public static final int OPT_HIDE_HANDLER_EDGES = 0x02;
	
	protected int flags = 0;
	protected List<BasicBlock> order;
	protected ControlFlowGraph graph;
	
	public int getFlags() {
			return flags;
		}
		
	public ControlFlowGraphDecorator setFlags(int flags) {
		this.flags = flags;
		return this;
	}
	
	public List<BasicBlock> getOrder() {
		return order;
	}
	
	public ControlFlowGraphDecorator setOrder(List<BasicBlock> order) {
		if(order != null) {
			this.order = order;
		}
		return this;
	}
	
	@Override
	public ControlFlowGraphDecorator setGraph(ControlFlowGraph graph) {
		this.graph = graph;
		order = new ArrayList<>(graph.vertices());
		return this;
	}
	
	@Override
	public void decorateNodeProperties(BasicBlock n, Map<String, Object> nprops) {
		nprops.put("shape", "box");
		nprops.put("labeljust", "l");
		
		if(graph.getEntries().contains(n)) {
			nprops.put("style", "filled");
			nprops.put("fillcolor", "red");
		} else if(graph.getEdges(n).size() == 0) {
			nprops.put("style", "filled");
			nprops.put("fillcolor", "green");
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append(order.indexOf(n)).append(". ").append(n.getId());
		if((flags & OPT_DEEP) != 0) {
			sb.append("\\l");
			StringBuilder sb2 = new StringBuilder();
			GraphUtils.printBlock(graph, sb2, n, 0, false, false);
			sb.append(sb2.toString().replace("\n", "\\l"));
		}
		nprops.put("label", sb.toString());
	}
	
	@Override
	public void decorateEdgeProperties(BasicBlock n, FlowEdge<BasicBlock> e, Map<String, Object> eprops) {
		if((flags & OPT_DEEP) != 0)
			eprops.put("label", e.toGraphString());
	}
	
	@Override
	public void decorateNodePrintability(BasicBlock n, AtomicBoolean printable) {
		printable.set(!n.isDummy());
	}
	
	@Override
	public void decorateEdgePrintability(BasicBlock n, FlowEdge<BasicBlock> e, AtomicBoolean printable) {
		printable.set(!((flags & OPT_HIDE_HANDLER_EDGES) != 0 && e instanceof TryCatchEdge));
	}
}
