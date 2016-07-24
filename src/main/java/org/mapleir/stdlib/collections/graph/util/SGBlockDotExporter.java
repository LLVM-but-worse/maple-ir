package org.mapleir.stdlib.collections.graph.util;

import java.util.List;

import org.mapleir.stdlib.cfg.BasicBlock;
import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.util.CFGDotExporter;
import org.mapleir.stdlib.ir.stat.Statement;

public class SGBlockDotExporter extends CFGDotExporter {
	public SGBlockDotExporter(ControlFlowGraph cfg, List<BasicBlock> order, String name, String fileExt) {
		super(cfg, order, name, fileExt);
	}
	
	@Override
	protected void printBlock(BasicBlock b, StringBuilder sb) {
		sb.append("\\l");
		for (Statement stmt : b.getStatements()) {
			sb.append(stmt.getId()).append(": ");
			sb.append(stmt.toString().replaceAll("\"", "\\\\\"").replaceAll("\n", "\\\\l")).append("\\l");
		}
	}
	
	@Override
	protected void printEdge(FlowEdge<BasicBlock> e, StringBuilder sb) {
		sb.append(e.getClass().getSimpleName().replace("Edge", ""));
	}
}