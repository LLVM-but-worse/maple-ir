package org.rsdeob.stdlib.cfg.ir.transform.impl;

import java.util.Map;
import java.util.Set;

import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementVisitor;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.exprtransform.DataFlowState;
import org.rsdeob.stdlib.cfg.ir.exprtransform.ExpressionEvaluator;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;

public class ValuePropagator {

	public static void propagateDefinitions(StatementGraph graph, DefinitionAnalyser da) {
		for(Statement stmt : graph.vertices()) {
			Map<String, Set<CopyVarStatement>> in = da.in(stmt);
			DataFlowState.CopySet copyset = new DataFlowState.CopySet(in);
			
			StatementVisitor impl = new StatementVisitor(stmt) {
				@Override
				public Statement visit(Statement s) {
					if(s instanceof VarExpression) {
						String name = s.toString();
						Set<CopyVarStatement> defs = in.get(name);
						
						if(defs != null && defs.size() == 1) {
							CopyVarStatement copy = defs.iterator().next();
							Expression def = copy.getExpression();
							Expression evaluated = ExpressionEvaluator.evaluate(def, copyset);
							if(ExpressionEvaluator.isConstant(evaluated) || def instanceof VarExpression) {
								int d = getDepth();
								getCurrent(d).overwrite(evaluated.copy(), getCurrentPtr(d));
							}
							if(def instanceof VarExpression) {
								// overwrite it here instead of in StatementVisitor
								int d = getDepth();
								getCurrent(d).overwrite(def.copy(), getCurrentPtr(d));
							} else {
								if(name.startsWith("svar")) {
									// not live out, but live in, so 
//									if(!la.out(stmt).get(name)) {
//										System.out.println("not live " + stmt);
//										int d = getDepth();
//										getCurrent(d).overwrite(def.copy(), getCurrentPtr(d));
//									}
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