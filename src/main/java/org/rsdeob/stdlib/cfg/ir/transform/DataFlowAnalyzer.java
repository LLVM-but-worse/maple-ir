package org.rsdeob.stdlib.cfg.ir.transform;

import static org.rsdeob.stdlib.cfg.ir.transform.DataFlowExpression.*;
import static org.rsdeob.stdlib.cfg.ir.transform.DataFlowState.CopySet.AllVarsExpression.VAR_ALL;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Queue;

import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementGraphBuilder;
import org.rsdeob.stdlib.cfg.ir.stat.ConditionalJumpStatement;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.ir.stat.SwitchStatement;
import org.rsdeob.stdlib.cfg.ir.stat.UnconditionalJumpStatement;
import org.rsdeob.stdlib.cfg.ir.transform.DataFlowState.CopySet;

public class DataFlowAnalyzer {
	private final StatementGraph sgraph;
	private final LinkedHashMap<Statement, DataFlowState> flowStates;
	private final Queue<Statement> worklist;

	public DataFlowAnalyzer(ControlFlowGraph cfg) {
		if (cfg.getEntries().size() != 1)
			throw new IllegalArgumentException("ControlFlowGraph has more than one entry!");

		sgraph = StatementGraphBuilder.create(cfg);
		if (sgraph.getEntries().size() != 1)
			throw new IllegalArgumentException("StatementGraph has more than one entry!");

		flowStates = new LinkedHashMap<>();

		DataFlowState entryFlowState = new DataFlowState();
		entryFlowState.in.put(VAR_ALL, new CopyVarStatement(VAR_ALL, NOT_A_CONST));
		flowStates.put(sgraph.getEntries().iterator().next(), entryFlowState);

		// define every other nodes in and out being undefined
		CopyVarStatement allUndefined = new CopyVarStatement(VAR_ALL, UNDEFINED);
		for (Statement stmt : sgraph.vertices()) {
			if (sgraph.getEntries().contains(stmt))
				continue;
			DataFlowState state = new DataFlowState();
			state.in.put(VAR_ALL, allUndefined);
			state.out.put(VAR_ALL, allUndefined);
			flowStates.put(stmt, state);
		}

		worklist = new LinkedList<>();
		worklist.add(sgraph.getEntries().iterator().next());
	}

	public LinkedHashMap<Statement, DataFlowState> compute() {
		while (!worklist.isEmpty()) {
			Statement stmt = worklist.remove();
			DataFlowState state = flowStates.get(stmt);

			CopySet out = new CopySet(state.out);
			if (!sgraph.getEntries().contains(stmt)) {
				CopySet newIn = null;
				for (FlowEdge<Statement> pred : sgraph.getReverseEdges(stmt))
					if (sgraph.isExecutable(pred))
						if (newIn == null) {
							newIn = new CopySet(flowStates.get(pred.src).out);
						} else {
							newIn = newIn.meet(flowStates.get(pred.src).out);
						}
				state.in = newIn;
			}

			if (stmt instanceof CopyVarStatement) {
				// initialise a new CopySet for the out
				state.out = new CopySet(state.in);
				// propagate the variable information
				state.out.transfer((CopyVarStatement) stmt);
				for (FlowEdge<Statement> succ : sgraph.getEdges(stmt))
					sgraph.markExecutable(succ);
			} else if (stmt instanceof UnconditionalJumpStatement) {
				// initialise a new CopySet for the out
				state.out = new CopySet(state.in);
				for (FlowEdge<Statement> succ : sgraph.getEdges(stmt)) {
					sgraph.markExecutable(succ);
				}
			} else if (stmt instanceof ConditionalJumpStatement) { // TODO: compute which branch is correct
				// initialise a new CopySet for the out
				state.out = new CopySet(state.in);
				for (FlowEdge<Statement> succ : sgraph.getEdges(stmt)) {
					sgraph.markExecutable(succ);
				}
			} else if (stmt instanceof SwitchStatement) { // TODO: compute which case is correct
				// initialise a new CopySet for the out
				state.out = new CopySet(state.in);
				for (FlowEdge<Statement> succ : sgraph.getEdges(stmt)) {
					sgraph.markExecutable(succ);
				}
			} else {
				// initialise a new CopySet for the out
				state.out = new CopySet(state.in);
				for (FlowEdge<Statement> succ : sgraph.getEdges(stmt)) {
					sgraph.markExecutable(succ);
				}
			}
			// if the out set changed
			if (!state.out.equals(out)) {
				// re-process it
				for (FlowEdge<Statement> succ : sgraph.getEdges(stmt))
					worklist.add(succ.dst);
			}
		}
		return flowStates;
	}
}
