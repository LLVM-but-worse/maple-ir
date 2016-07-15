package org.rsdeob.stdlib.ir.export;

import org.rsdeob.stdlib.collections.graph.util.DotExporter;
import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.header.HeaderStatement;
import org.rsdeob.stdlib.ir.stat.Statement;

public class SGDotExporter extends DotExporter<StatementGraph, Statement> {
	public SGDotExporter(StatementGraph graph, CodeBody order, String name, String fileExt) {
		super(graph, order.stmts(), name, fileExt); // todo make CodeBody implement List
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
}
