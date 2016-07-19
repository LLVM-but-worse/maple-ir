package org.rsdeob.stdlib.ir;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.collections.graph.util.DotExporter;
import org.rsdeob.stdlib.ir.header.HeaderStatement;
import org.rsdeob.stdlib.ir.stat.Statement;

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
