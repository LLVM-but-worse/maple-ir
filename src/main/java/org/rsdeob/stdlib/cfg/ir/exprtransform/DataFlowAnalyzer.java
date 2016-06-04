package org.rsdeob.stdlib.cfg.ir.exprtransform;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.ir.exprtransform.DataFlowState.CopySet;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

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
					boolean contains = false;
					for (CopyVarStatement copy : allCopies) {
						if (copy.valueEquals(stmt)) {
							contains = true;
							break;
						}
					}
					if (!contains)
						allCopies.add((CopyVarStatement) stmt);
				}
			}
		}
	}

	// compute forward data flow (available expressions)
	public Map<Statement, DataFlowState> computeForward() {
		HashMap<BasicBlock, DataFlowState> dataFlow = new HashMap<>();

		// Compute first block
		for(BasicBlock entry : cfg.getEntries()) {
			dataFlow.put(entry, computeFirstBlock(entry));
		}

		// Compute initial out for each block
		for (BasicBlock b : cfg.vertices()) {
			if (cfg.getEntries().contains(b))
				continue;
			DataFlowState state = compute(b);
			for (CopyVarStatement copy : allCopies) {
				if (b.getStatements().contains(copy))
					continue;
				if (state.kill.contains(copy))
					continue;
				state.out.put(copy.getVariable().toString(), copy);
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
						state.out.put(copy.getVariable().toString(), copy);
				for (CopyVarStatement copy : state.gen)
					state.out.put(copy.getVariable().toString(), copy);

				dataFlow.put(b, state);
				if (!state.out.equals(oldOut)) {
					changed = true;
					System.out.println("Data flow for " + b.getId());
					System.out.println("In:");
					for (CopyVarStatement copy : state.in.values())
						System.out.println("    " + copy);
					System.out.println("Out:");
					for (CopyVarStatement copy : state.out.values())
						System.out.println("    " + copy);
					System.out.println("\n");
				}
			}
		}

		HashMap<Statement, DataFlowState> result = new HashMap<>();
		for (Map.Entry<BasicBlock, DataFlowState> entry : dataFlow.entrySet())
			for (Statement stmt : entry.getKey().getStatements())
				result.put(stmt, entry.getValue());
		return result;
	}

	private DataFlowState computeFirstBlock(BasicBlock entry) {
		DataFlowState state = compute(entry);
		for (CopyVarStatement copy : state.gen)
			state.out.put(copy.getVariable().toString(), copy);

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
			if (checkRhs)
				for (Iterator<Map.Entry<String, CopyVarStatement>> it = gen.entrySet().iterator(); it.hasNext();)
					if (it.next().getValue().isAffectedBy(stmt))
						it.remove();
			CopyVarStatement newCopy = (CopyVarStatement) stmt;
			gen.put(newCopy.getVariable().toString(), newCopy);
		}

		return new HashSet<>(gen.values());
	}

	// KILL[b] = all copies anywhere in the cfg that do not have lhs/rhs redefined in b
	private HashSet<CopyVarStatement> computeKill(BasicBlock b) {
		HashSet<CopyVarStatement> kill = new HashSet<>();
		for (CopyVarStatement copy : allCopies) {
			if (b.getStatements().contains(copy))
				continue;

			for (Statement stmt : b.getStatements()) {
				if (!(stmt instanceof CopyVarStatement))
					continue;
				CopyVarStatement newCopy = (CopyVarStatement) stmt;

				// Add all existing statements that would be overwritten by this
				if (copy.getVariable().toString().equals(newCopy.getVariable().toString())) { // check lhs
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
