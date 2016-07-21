package org.mapleir.stdlib.collections.graph.util;

import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.ir.CodeBody;
import org.mapleir.stdlib.ir.StatementGraph;
import org.mapleir.stdlib.ir.header.HeaderStatement;
import org.mapleir.stdlib.ir.stat.Statement;

public class SGDotExporter extends DotExporter<StatementGraph, Statement> {
	public SGDotExporter(StatementGraph graph, CodeBody order, String name, String fileExt) {
		super(graph, order, name, fileExt);
	}

	@Override
	protected boolean filterBlock(Statement stmt) {
		return !(stmt instanceof HeaderStatement);
	}

	@Override
	protected void printBlock(Statement stmt, StringBuilder sb) {
		sb.append(": ");
		sb.append(stmt.toString().replaceAll("\"", "\\\\\""));
	}

	@Override
	protected void printEdge(FlowEdge<Statement> e, StringBuilder sb) {
		sb.append(e.getClass().getSimpleName().replace("Edge", ""));
	}
}
