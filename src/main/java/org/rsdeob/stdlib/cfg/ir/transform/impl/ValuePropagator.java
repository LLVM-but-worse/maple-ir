package org.rsdeob.stdlib.cfg.ir.transform.impl;

import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementVisitor;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.exprtransform.DataFlowState;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.util.ExpressionEvaluator;

import java.util.Map;
import java.util.Set;

public class ValuePropagator {
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
						}
					}
					return s;
				}
			};
			impl.visit();
		}
	}
}