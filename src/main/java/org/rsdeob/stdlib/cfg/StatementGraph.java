package org.rsdeob.stdlib.cfg;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.stat.Statement;
import org.rsdeob.stdlib.collections.graph.FastGraph;

public class StatementGraph extends FastGraph<Statement, FlowEdge<Statement>>{

	@Override
	public Statement getEntry() {
		return null;
	}
}