package org.rsdeob.stdlib.cfg.ir.transform;

import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementGraphBuilder;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

import static org.rsdeob.stdlib.cfg.ir.transform.DataFlowExpression.BOTTOM_EXPR;
import static org.rsdeob.stdlib.cfg.ir.transform.DataFlowExpression.TOP_EXPR;
import static org.rsdeob.stdlib.cfg.ir.transform.DataFlowState.CopySet.AllVarsExpression.VAR_ALL;

public class DataFlowAnalyzer {
	private final ControlFlowGraph cfg;
	private final StatementGraph sg;
	private final HashMap<Statement, DataFlowState> dataFlow;
	private final Queue<Statement> worklist;

	public DataFlowAnalyzer(ControlFlowGraph cfg) {
		this.cfg = cfg;
		if (cfg.getEntries().size() != 1)
			throw new IllegalArgumentException("ControlFlowGraph has more than one entry!");

		sg = StatementGraphBuilder.create(cfg);
		if (sg.getEntries().size() != 1)
			throw new IllegalArgumentException("StatementGraph has more than one entry!");

		dataFlow = new HashMap<>();
		DataFlowState state;

		state = new DataFlowState();
		state.in.put(VAR_ALL, new CopyVarStatement(VAR_ALL, BOTTOM_EXPR));
		dataFlow.put(sg.getEntries().iterator().next(), state);

		CopyVarStatement allTop = new CopyVarStatement(VAR_ALL, TOP_EXPR);
		for (Statement stmt : sg.vertices()) {
			if (sg.getEntries().contains(stmt))
				continue;
			state = new DataFlowState();
			state.in.put(VAR_ALL, allTop);
			state.out.put(VAR_ALL, allTop);
		}

		worklist = new LinkedList<>();
		worklist.add(sg.getEntries().iterator().next());
	}

	public HashMap<Statement, DataFlowState> compute() {
		// todo
		return dataFlow;
	}
}
