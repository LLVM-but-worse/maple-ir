package org.rsdeob.stdlib.cfg.ir.transform.impl;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.ir.RootStatement;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementVisitor;
import org.rsdeob.stdlib.cfg.ir.expr.ConstantExpression;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.exprtransform.DataFlowState;
import org.rsdeob.stdlib.cfg.ir.exprtransform.ExpressionEvaluator;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;

public class ValuePropagator {

	public static int propagateDefinitions1(RootStatement root, StatementGraph graph, DefinitionAnalyser da) {
		AtomicInteger propagated = new AtomicInteger();
		
		for (Statement stmt : graph.vertices()) {
			Map<String, Set<CopyVarStatement>> in = da.in(stmt);

			StatementVisitor impl = new StatementVisitor(stmt) {
				@Override
				public Statement visit(Statement s) {
					if (s instanceof VarExpression) {
						Set<CopyVarStatement> defs = in.get(s.toString());

						if (defs.size() == 1) {
							CopyVarStatement def = defs.iterator().next();
							Expression rhs = def.getExpression();

							if (rhs instanceof ConstantExpression) {
								// constant propagation is easy
								propagated.incrementAndGet();
								return rhs.copy();

							} else {
								AtomicBoolean canProp = new AtomicBoolean(true);

								if (rhs instanceof VarExpression) {
									// hold onto your seat...

									Set<CopyVarStatement> rhsDefs = in.get(rhs.toString());
									if (rhsDefs.size() == 1) {
										CopyVarStatement rhsDef = rhsDefs.iterator().next();
										Expression tail = rhsDef.getExpression();

										if (tail instanceof VarExpression) {

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
										}
									}
								}
							}
						}
					}
					return s;
				}
			};
			impl.visit();
		}
		
		return propagated.get();
	}

	private static void propagateDefinitions(StatementGraph graph, DefinitionAnalyser da) {
		for (Statement stmt : graph.vertices()) {
			Map<String, Set<CopyVarStatement>> in = da.in(stmt);
			DataFlowState.CopySet copyset = new DataFlowState.CopySet(in);

			StatementVisitor impl = new StatementVisitor(stmt) {
				@Override
				public Statement visit(Statement s) {
					if (s instanceof VarExpression) {
						String name = s.toString();
						Set<CopyVarStatement> defs = in.get(name);

						if (defs != null && defs.size() == 1) {
							CopyVarStatement copy = defs.iterator().next();
							Expression defValue = copy.getExpression();

							Expression evaluated = ExpressionEvaluator.evaluate(defValue, copyset);
							if (ExpressionEvaluator.isConstant(evaluated)) {
								int d = getDepth();
								getCurrent(d).overwrite(evaluated.copy(), getCurrentPtr(d));
							}

							if (defValue instanceof VarExpression) {
								// must check that the defValue also has 1 def here
								Set<CopyVarStatement> exprDefs = da.in(stmt).get(defValue.toString());
								System.out.println("redundant " + stmt + "   exprdefs  " + exprDefs);
								int d = getDepth();
								getCurrent(d).overwrite(defValue.copy(), getCurrentPtr(d));
							} else {
								if (name.startsWith("svar")) {
									// not live out, but live in, so
									// if(!la.out(stmt).get(name)) {
									// System.out.println("not live " + stmt);
									// int d = getDepth();
									// getCurrent(d).overwrite(def.copy(), getCurrentPtr(d));
									// }
								}
							}
						}
					}
					return s;
				}
			};
			impl.visit();
		}
	}
}