package org.rsdeob.stdlib.cfg.ir.transform;

import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementGraphBuilder;
import org.rsdeob.stdlib.cfg.ir.stat.*;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Queue;

import static org.rsdeob.stdlib.cfg.ir.transform.DataFlowExpression.BOTTOM_EXPR;
import static org.rsdeob.stdlib.cfg.ir.transform.DataFlowExpression.TOP_EXPR;
import static org.rsdeob.stdlib.cfg.ir.transform.DataFlowState.CopySet;
import static org.rsdeob.stdlib.cfg.ir.transform.DataFlowState.CopySet.AllVarsExpression.VAR_ALL;

public class DataFlowAnalyzer {
	private final ControlFlowGraph cfg;
	private final StatementGraph sg;
	private final LinkedHashMap<Statement, DataFlowState> dataFlow;
	private final Queue<Statement> worklist;

	public DataFlowAnalyzer(ControlFlowGraph cfg) {
		this.cfg = cfg;
		if (cfg.getEntries().size() != 1)
			throw new IllegalArgumentException("ControlFlowGraph has more than one entry!");

		sg = StatementGraphBuilder.create(cfg);
		if (sg.getEntries().size() != 1)
			throw new IllegalArgumentException("StatementGraph has more than one entry!");

		dataFlow = new LinkedHashMap<>();
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
			dataFlow.put(stmt, state);
		}

		worklist = new LinkedList<>();
		worklist.add(sg.getEntries().iterator().next());
	}

	public LinkedHashMap<Statement, DataFlowState> compute() {
		while (!worklist.isEmpty()) {
			Statement stmt = worklist.remove();
			DataFlowState state = dataFlow.get(stmt);

			CopySet out = new CopySet(state.out);
			if (!sg.getEntries().contains(stmt)) {
				CopySet in = null;
				for (FlowEdge<Statement> pred : sg.getReverseEdges(stmt))
					if (sg.isExecutable(pred))
						if (in == null) in = new CopySet(dataFlow.get(pred.src).out);
						else in = in.meet(dataFlow.get(pred.src).out);
				state.in = in;
			}

			if (stmt instanceof CopyVarStatement) {
				state.out = new CopySet(state.in);
				state.out.trans((CopyVarStatement) stmt);
				for (FlowEdge<Statement> succ : sg.getEdges(stmt))
					sg.markExecutable(succ);
			} else if (stmt instanceof UnconditionalJumpStatement) {
				state.out = new CopySet(state.in);
				for (FlowEdge<Statement> succ : sg.getEdges(stmt)) {
					sg.markExecutable(succ);
				}
			} else if (stmt instanceof ConditionalJumpStatement) { // TODO: compute which branch is correct
				state.out = new CopySet(state.in);
				for (FlowEdge<Statement> succ : sg.getEdges(stmt)) {
					sg.markExecutable(succ);
				}
			} else if (stmt instanceof SwitchStatement) { // TODO: compute which case is correct
				state.out = new CopySet(state.in);
				for (FlowEdge<Statement> succ : sg.getEdges(stmt)) {
					sg.markExecutable(succ);
				}
			} else {
				state.out = new CopySet(state.in);
				for (FlowEdge<Statement> succ : sg.getEdges(stmt)) {
					sg.markExecutable(succ);
				}
			}
			if (!state.out.equals(out))
				for (FlowEdge<Statement> succ : sg.getEdges(stmt))
					worklist.add(succ.dst);
		}
		return dataFlow;
	}
}
