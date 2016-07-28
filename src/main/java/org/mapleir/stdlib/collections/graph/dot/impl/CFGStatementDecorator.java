package org.mapleir.stdlib.collections.graph.dot.impl;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.ir.stat.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CFGStatementDecorator extends ControlFlowGraphDecorator {
	@Override
	public void decorateNodeProperties(ControlFlowGraph g, BasicBlock n, Map<String, Object> nprops) {
		super.decorateNodeProperties(g, n, nprops);
		List<BasicBlock> order = new ArrayList<>(g.vertices());
		
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
		nprops.put("label", sb.toString());
	}

	@Override
	public void decorateEdgeProperties(ControlFlowGraph g, BasicBlock n, FlowEdge<BasicBlock> e, Map<String, Object> eprops) {
		if((flags & OPT_DEEP) != 0)
			eprops.put("label", e.getClass().getSimpleName().replace("Edge", ""));
	}
}
