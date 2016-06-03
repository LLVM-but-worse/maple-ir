package org.rsdeob.stdlib.cfg.ir.transform.impl;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.ir.RootStatement;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementVisitor;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CopyPropagator {
	public static int propagateDefinitions(RootStatement root, StatementGraph graph, DefinitionAnalyser da) {
		AtomicInteger propagated = new AtomicInteger();

		for (Statement stmt : graph.vertices()) {
			Map<String, Set<CopyVarStatement>> in = da.in(stmt);

			StatementVisitor impl = new StatementVisitor(stmt) {
				@Override
				public Statement visit(Statement s) {
					if (!(s instanceof VarExpression))
						return s;
					Set<CopyVarStatement> defs = in.get(s.toString());

					if (defs.size() != 1)
						return s;
					CopyVarStatement def = defs.iterator().next();
					Expression rhs = def.getExpression();

					AtomicBoolean canProp = new AtomicBoolean(true);

					if (!(rhs instanceof VarExpression))
						return s;
					Set<CopyVarStatement> rhsDefs = in.get(rhs.toString());
					if (rhsDefs.size() != 1)
						return s;

					CopyVarStatement rhsDef = rhsDefs.iterator().next();
					Expression tail = rhsDef.getExpression();

					if (!(tail instanceof VarExpression))
						return s;

					System.out.println("Statement: " + stmt);
					System.out.println(" rdef of " + s + ": " + def);
					System.out.println(" rhs: " + rhs);
					System.out.println(" rdef of " + rhs + ": " + rhsDef);
					System.out.println(" tail: " + tail);

					// tail must not be redefined between
					// def and rhsDef

					// one way to calculate this is to check
					// this is:
					// function defs(var, stmt)
					// if(defs(tail, def) == defs(tail, rhsDef)) then propagate
					// ... or not.

					StatementVisitor vis = new StatementVisitor(root) {
						boolean start = false;

						@Override
						public Statement visit(Statement s) {
							if (!start) {
								if (s == tail) {
									start = true;
								}
								return s;
							}

							// check only until the next block boundary.
							Set<FlowEdge<Statement>> pes = graph.getReverseEdges(s);
							if (pes != null && pes.size() > 1) {
								_break();
								return s;
							}

							if (s instanceof CopyVarStatement) {
								CopyVarStatement cp = (CopyVarStatement) s;
								VarExpression cpv = cp.getVariable();
								if (cpv.toString().equals(tail.toString())) {
									canProp.set(false);
								}
							}

							return s;
						}
					};
					vis.visit();

					if (canProp.get()) {
						propagated.incrementAndGet();
						return rhs.copy();
					}
					return s;
				}
			};
			impl.visit();
		}

		return propagated.get();
	}
}
