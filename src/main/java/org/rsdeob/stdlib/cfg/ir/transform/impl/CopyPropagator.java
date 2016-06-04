package org.rsdeob.stdlib.cfg.ir.transform.impl;

import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.ir.RootStatement;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementGraphBuilder;
import org.rsdeob.stdlib.cfg.ir.StatementVisitor;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.exprtransform.DataFlowAnalyzer;
import org.rsdeob.stdlib.cfg.ir.exprtransform.DataFlowState;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.util.ExpressionEvaluator;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class CopyPropagator {
	public static int propagateDefinitions(ControlFlowGraph cfg, RootStatement root, DefinitionAnalyser da) {
		Map<Statement, DataFlowState> dataFlow = new DataFlowAnalyzer(cfg, true).computeForward(); // rhs mode on
		StatementGraph sg = StatementGraphBuilder.create(cfg);

		int propagated = 0;
		for (Statement stmt : sg.vertices()) {
			if (!(stmt instanceof CopyVarStatement))
				continue;
			AtomicBoolean canPropagate = new AtomicBoolean(true);
			CopyVarStatement copy = (CopyVarStatement) stmt;

			Set<Statement> uses = da.getUses(copy);
			for (Statement u : uses) {
				canPropagate.set(false);
				if (da.in(u).get(copy.getVariable().toString()).size() == 1) {
					DataFlowState.CopySet in = dataFlow.get(u).in;
					if (in.containsKey(copy.getVariable().toString()) && in.get(copy.getVariable().toString()) == copy)
						canPropagate.set(true);
				}
			}

			if (canPropagate.get()) {
				propagated++;
				new StatementVisitor(root) {
					@Override
					public Statement visit(Statement stmt) {
						if (stmt instanceof Expression && uses.contains(stmt))
							return ExpressionEvaluator.evaluate((Expression) stmt, dataFlow.get(stmt).in);
						else
							return stmt;
					}
				}.visit();
			}
		}

		return propagated;
	}
}
