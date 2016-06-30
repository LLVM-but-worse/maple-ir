package org.rsdeob.stdlib.ir.transform.impl;

import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.Local;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.expr.VarExpression;
import org.rsdeob.stdlib.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.ir.stat.Statement;

public class DeadAssignmentEliminator extends Transformer {

	public DeadAssignmentEliminator(CodeBody code, CodeAnalytics analytics) {
		super(code, analytics);
	}

	@Override
	public int run() {
		StatementGraph graph = analytics.sgraph;
		LivenessAnalyser la = analytics.liveness;

		AtomicInteger dead = new AtomicInteger();
		for(Statement stmt : new HashSet<>(graph.vertices())) {
			if(stmt instanceof CopyVarStatement) {
				Map<Local, Boolean> out = la.out(stmt);
				CopyVarStatement copy = (CopyVarStatement) stmt;
				VarExpression var = copy.getVariable();
				
				if(!out.get(var.getLocal())) {
					code.remove(copy);
					code.commit();
					dead.incrementAndGet();
				}
			}
		}
		return dead.get();
	}
}