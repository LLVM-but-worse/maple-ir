package org.rsdeob.stdlib.cfg.ir;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.util.GraphUtils;
import org.rsdeob.stdlib.collections.graph.flow.FlowGraph;

public class StatementGraph extends FlowGraph<Statement, FlowEdge<Statement>>{
	
	@Override
	public String toString() {
		return GraphUtils.toString(this, vertices());
	}
}