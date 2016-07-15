package org.rsdeob.stdlib.ir.transform.impl;

import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.rsdeob.AnalyticsTest;
import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.StatementVisitor;
import org.rsdeob.stdlib.ir.expr.InitialisedObjectExpression;
import org.rsdeob.stdlib.ir.expr.InvocationExpression;
import org.rsdeob.stdlib.ir.expr.VarExpression;
import org.rsdeob.stdlib.ir.locals.Local;
import org.rsdeob.stdlib.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.ir.stat.PopStatement;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.transform.Transformer;

public class DeadAssignmentEliminator extends Transformer {

	public DeadAssignmentEliminator(CodeBody code, CodeAnalytics analytics) {
		super(code, analytics);
	}

	@Override
	public int run() {
		StatementGraph graph = analytics.sgraph;
		LivenessAnalyser la = new LivenessAnalyser(graph);

		AtomicInteger dead = new AtomicInteger();
		for(Statement stmt : new HashSet<>(graph.vertices())) {
			if(stmt instanceof CopyVarStatement) {
				Map<Local, Boolean> out = la.out(stmt);
				CopyVarStatement copy = (CopyVarStatement) stmt;
				VarExpression var = copy.getVariable();
				
				if(copy.getExpression() instanceof VarExpression && ((VarExpression) copy.getExpression()).getLocal() == var.getLocal()) {
					code.remove(stmt);
					code.commit();
					AnalyticsTest.verify_callback(code, analytics, stmt);
					dead.incrementAndGet();
					continue;
				}
				
				if(!out.get(var.getLocal())) {
					AtomicBoolean complex = new AtomicBoolean(false);
					new StatementVisitor(copy) {
						@Override
						public Statement visit(Statement stmt) {
							if(stmt instanceof InvocationExpression || stmt instanceof InitialisedObjectExpression) {
								complex.set(true);
								_break();
							}
							return stmt;
						}
					}.visit();
					if(complex.get()) {
						code.replace(copy, new PopStatement(copy.getExpression()));
						code.commit();
					} else {
						code.remove(copy);
						code.commit();
					}
					
					AnalyticsTest.verify_callback(code, analytics, stmt);
					dead.incrementAndGet();
				}
			}
		}
		return dead.get();
	}
}