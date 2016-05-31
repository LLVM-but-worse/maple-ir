package org.rsdeob.stdlib.cfg.ir.transform.impl;

import java.util.Map;

import org.rsdeob.stdlib.cfg.ir.RootStatement;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;

public class DeadAssignmentEliminator {

	public static void run(RootStatement root, StatementGraph graph, LivenessAnalyser la) {
		for(Statement stmt : graph.vertices()) {
			if(stmt instanceof CopyVarStatement) {
				Map<String, Boolean> out = la.out(stmt);
				
				CopyVarStatement copy = (CopyVarStatement) stmt;
				VarExpression var = copy.getVariable();
				
				if(!out.get(var.toString())) {
					root.delete(root.indexOf(copy));
				}
			}
		}
	}
}