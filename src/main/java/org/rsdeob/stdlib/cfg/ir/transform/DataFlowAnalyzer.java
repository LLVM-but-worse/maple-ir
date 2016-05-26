package org.rsdeob.stdlib.cfg.ir.transform;

import java.util.HashMap;
import java.util.HashSet;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.ir.transform.DataFlowState.CopySet;

public class DataFlowAnalyzer {
	private final ControlFlowGraph cfg;
	private boolean checkRhs;
	private final HashSet<CopyVarStatement> allCopies;

	public DataFlowAnalyzer(ControlFlowGraph cfg, boolean checkRhs) {
		this.cfg = cfg;
		this.checkRhs = checkRhs;

		allCopies = new HashSet<>();
		for (BasicBlock b : cfg.vertices()) {
			for (Statement stmt : b.getStatements()) {
				if (stmt instanceof CopyVarStatement) {
					allCopies.add((CopyVarStatement) stmt);
				}
			}
		}
	}

	// compute forward data flow (available expressions)
	public HashMap<BasicBlock, DataFlowState> computeForward() {
		HashMap<BasicBlock, DataFlowState> dataFlow = new HashMap<>();

		// Compute first block
		for(BasicBlock entry : cfg.getEntries()) {
			dataFlow.put(entry, computeFirstBlock());
		}

		// Compute initial out for each block
		for (BasicBlock b : cfg.vertices()) {
			if (cfg.getEntries().contains(b))
				continue;
			DataFlowState state = compute(b);
			for (CopyVarStatement copy : allCopies) {
				if (copy.getBlock() == b)
					continue;
				if (state.kill.contains(copy))
					continue;
				state.out.put(copy.getVariable(), copy);
			}
			dataFlow.put(b, state);
		}

		for (boolean changed = true; changed;) {
			changed = false;

			for (BasicBlock b : cfg.vertices()) {
				if (cfg.getEntries().contains(b))
					continue;

				DataFlowState state = dataFlow.get(b);
				CopySet oldOut = new CopySet(state.out);

				// IN[b] = MEET(OUT[p] for p in predicates(b))
				CopySet in = new CopySet();
				for (FlowEdge<BasicBlock> e : b.getPredecessors())
					in = in.isEmpty() ? dataFlow.get(e.src).out : in.meet(dataFlow.get(e.src).out);
				state.in = in;

				// OUT[b] = GEN[b] UNION (IN[b] - KILL[b])
				for (CopyVarStatement copy : state.in.values())
					if (!state.kill.contains(copy))
						state.out.put(copy.getVariable(), copy);
				for (CopyVarStatement copy : state.gen)
					state.out.put(copy.getVariable(), copy);

				dataFlow.put(b, state);
				if (!state.out.equals(oldOut))
					changed = true;
			}
		}

		return dataFlow;
	}

	private DataFlowState computeFirstBlock() {
		DataFlowState state = compute(cfg.getEntries().iterator().next());
		for (CopyVarStatement copy : state.gen)
			state.out.put(copy.getVariable(), copy);
		return state;
	}

	private DataFlowState compute(BasicBlock b) {
		return new DataFlowState(computeGen(b), computeKill(b));
	}

	// GEN[b] = copies in b that reach end of block (no lhs or rhs redefinition)
	private HashSet<CopyVarStatement> computeGen(BasicBlock b) {
		CopySet gen = new CopySet();
		for (Statement stmt : b.getStatements()) {
			if (!(stmt instanceof CopyVarStatement))
				continue;
			CopyVarStatement newCopy = (CopyVarStatement) stmt;
			gen.put(newCopy.getVariable(), newCopy);
		}

		return new HashSet<>(gen.values());
	}

	// KILL[b] = all copies anywhere in the cfg that do not have lhs/rhs redefined in b
	private HashSet<CopyVarStatement> computeKill(BasicBlock b) {
		HashSet<CopyVarStatement> kill = new HashSet<>();
		for (CopyVarStatement copy : allCopies) {
			if (copy.getBlock() == b)
				continue;

			for (Statement stmt : b.getStatements()) {
				if (!(stmt instanceof CopyVarStatement))
					continue;
				CopyVarStatement newCopy = (CopyVarStatement) stmt;

				// Add all existing statements that would be overwritten by this
				if (copy.getVariable().equals(newCopy.getVariable())) { // check lhs
					kill.add(copy);
					break;
				}
				if (checkRhs && copy.isAffectedBy(newCopy)) {
					kill.add(copy);
					break;
				}
			}
		}

		return kill;
	}
}
