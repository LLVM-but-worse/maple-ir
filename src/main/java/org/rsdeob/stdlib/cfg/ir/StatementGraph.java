package org.rsdeob.stdlib.cfg.ir;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.util.GraphUtils;
import org.rsdeob.stdlib.collections.graph.FastGraph;

public class StatementGraph extends FastGraph<Statement, FlowEdge<Statement>>{
	public String toString() {
		return GraphUtils.toString(this, vertices());
	}
}