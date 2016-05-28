package org.rsdeob.stdlib.cfg.ir;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.util.GraphUtils;
import org.rsdeob.stdlib.collections.graph.flow.FlowGraph;

import java.util.HashMap;

public class StatementGraph extends FlowGraph<Statement, FlowEdge<Statement>>{
	private final HashMap<Integer, Boolean> executableMap = new HashMap<>();

	@Override
	public void addEdge(Statement stmt, FlowEdge<Statement> edge) {
		super.addEdge(stmt, edge);
		executableMap.put(hash(edge), false);
	}

	private int hash(FlowEdge<Statement> edge) {
		int result = edge.src.hashCode();
		result = 31 * result + edge.dst.hashCode();
		return result;
	}

	public boolean isExecutable(FlowEdge<Statement> edge) {
		return executableMap.get(hash(edge));
	}

	public void markExecutable(FlowEdge<Statement> edge) {
		executableMap.put(hash(edge), true);
	}

	@Override
	public String toString() {
		return GraphUtils.toString(this, vertices());
	}
}