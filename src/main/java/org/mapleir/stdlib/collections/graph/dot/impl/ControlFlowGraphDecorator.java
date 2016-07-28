package org.mapleir.stdlib.collections.graph.dot.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.edge.TryCatchEdge;
import org.mapleir.stdlib.collections.graph.dot.DotPropertyDecorator;
import org.mapleir.stdlib.collections.graph.util.GraphUtils;

public class ControlFlowGraphDecorator implements DotPropertyDecorator<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> {
	public static final int OPT_DEEP = 0x01;
	public static final int OPT_HIDE_HANDLER_EDGES = 0x02;
	
	protected int flags = 0;
	
	public int getFlags() {
			return flags;
		}
		
	public ControlFlowGraphDecorator setFlags(int flags) {
		this.flags = flags;
		return this;
	}
	
	@Override
	public void decorateNodeProperties(ControlFlowGraph g, BasicBlock n, Map<String, Object> nprops) {
		List<BasicBlock> order = new ArrayList<>(g.vertices());
		
		nprops.put("shape", "box");
		nprops.put("labeljust", "l");
		
		if(g.getEntries().contains(n)) {
			nprops.put("style", "filled");
			nprops.put("fillcolor", "red");
		} else if(g.getEdges(n).size() == 0) {
			nprops.put("style", "filled");
			nprops.put("fillcolor", "green");
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append(order.indexOf(n)).append(". ").append(n.getId());
		if((flags & OPT_DEEP) != 0) {
			sb.append("\\l");
			StringBuilder sb2 = new StringBuilder();
			GraphUtils.printBlock(g, sb2, n, 0, false, false);
			sb.append(sb2.toString().replace("\n", "\\l"));
		}
		nprops.put("label", sb.toString());
	}
	
	@Override
	public void decorateEdgeProperties(ControlFlowGraph g, BasicBlock n, FlowEdge<BasicBlock> e, Map<String, Object> eprops) {
		if((flags & OPT_DEEP) != 0)
			eprops.put("label", e.toGraphString());
	}
	
	@Override
	public boolean isNodePrintable(ControlFlowGraph g, BasicBlock n) {
		return !n.isDummy();
	}
	
	@Override
	public boolean isEdgePrintable(ControlFlowGraph g, BasicBlock n, FlowEdge<BasicBlock> e) {
		return !((flags & OPT_HIDE_HANDLER_EDGES) != 0 && e instanceof TryCatchEdge);
	}
}
