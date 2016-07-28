package org.mapleir.stdlib.collections.graph.dot.impl;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.graph.dot.DotConfiguration;
import org.mapleir.stdlib.ir.stat.Statement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CFGStatementDotWriter extends ControlFlowGraphDotWriter {
	public CFGStatementDotWriter(DotConfiguration<ControlFlowGraph, BasicBlock, FlowEdge<BasicBlock>> config, ControlFlowGraph graph) {
		super(config, graph);
	}
	
	@Override
	public Map<String, Object> getNodeProperties(BasicBlock n) {
		Map<String, Object> map = super.getNodeProperties(n);
		
		StringBuilder sb = new StringBuilder();
		sb.append(order.indexOf(n)).append(n.getId()).append(". ");
		if((flags & OPT_DEEP) != 0) {
			sb.append("\\l");
			List<Statement> statements = n.getStatements();
			for (int i = 0; i < statements.size(); i++) {
				Statement stmt = statements.get(i);
				sb.append(stmt.getId()).append(": ");
				sb.append(stmt.toString().replaceAll("\"", "\\\\\"").replaceAll("\n", "\\\\l"));
				if (i < statements.size() - 1)
					sb.append("\\l");
			}
		}
		map.put("label", sb.toString());
		
		return map;
	}
	
	@Override
	public Map<String, Object> getEdgeProperties(BasicBlock n, FlowEdge<BasicBlock> e) {
		if((flags & OPT_DEEP) != 0) {
			Map<String, Object> map = new HashMap<>(1);
			map.put("label", e.getClass().getSimpleName().replace("Edge", ""));
			return map;
		} else {
			return null;
		}
	}
}
