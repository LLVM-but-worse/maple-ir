package org.rsdeob.stdlib.ir.transform.impl;

import org.rsdeob.stdlib.ir.Local;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.StatementList;
import org.rsdeob.stdlib.ir.expr.VarExpression;
import org.rsdeob.stdlib.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.ir.stat.Statement;

import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class DeadAssignmentEliminator {

	public static int run(StatementList stmtList, CodeAnalytics analytics) {
		StatementGraph graph = analytics.stmtGraph;
		LivenessAnalyser la = analytics.liveness;

		AtomicInteger dead = new AtomicInteger();
		for(Statement stmt : new HashSet<>(graph.vertices())) {
			if(stmt instanceof CopyVarStatement) {
				Map<Local, Boolean> out = la.out(stmt);
				CopyVarStatement copy = (CopyVarStatement) stmt;
				VarExpression var = copy.getVariable();
				
				if(!out.get(var.getLocal())) {
					stmtList.remove(copy);
					dead.incrementAndGet();
				}
			}
		}
		return dead.get();
	}
}