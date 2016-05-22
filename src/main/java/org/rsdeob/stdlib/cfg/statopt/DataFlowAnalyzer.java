package org.rsdeob.stdlib.cfg.statopt;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.FlowEdge;
import org.rsdeob.stdlib.cfg.expr.Expression;
import org.rsdeob.stdlib.cfg.expr.VarExpression;
import org.rsdeob.stdlib.cfg.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.stat.Statement;

import java.util.HashMap;
import java.util.HashSet;

import static org.rsdeob.stdlib.cfg.statopt.DataFlowState.BOTTOM_EXPR;
import static org.rsdeob.stdlib.cfg.statopt.DataFlowState.TOP_EXPR;

public class DataFlowAnalyzer {
	private final ControlFlowGraph cfg;
	private final HashSet<CopyVarStatement> allCopies;

	public DataFlowAnalyzer(ControlFlowGraph cfg) {
		this.cfg = cfg;

		allCopies = new HashSet<>();
		for (BasicBlock b : cfg.blocks()) {
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
		dataFlow.put(cfg.getEntry(), computeFirstBlock());

		// Compute initial out for each block
		for (BasicBlock b : cfg.blocks()) {
			if (b == cfg.getEntry())
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

		boolean changed = true;
		int i = 0;
		while (changed) {
//			System.out.println("Iteration " + ++i);
			changed = false;
			HashMap<BasicBlock, DataFlowState> oldFlow = new HashMap<>(dataFlow);
			for (BasicBlock b : cfg.blocks()) {
				if (b == cfg.getEntry())
					continue;

				DataFlowState oldState = oldFlow.get(b);
				DataFlowState state = new DataFlowState(oldState.gen, oldState.kill);

				// IN[b] = MEET(OUT[p] for p in predicates(b))
				HashMap<VarExpression, CopyVarStatement> in = new HashMap<>();
				for (FlowEdge e : b.getPredecessors())
					in = in.isEmpty() ? oldFlow.get(e.src).out : meet(in, oldFlow.get(e.src).out);
				state.in = in;

				// OUT[b] = GEN[b] UNION (IN[b] - KILL[b])
				for (CopyVarStatement copy : state.in.values())
					if (!state.kill.contains(copy))
						state.out.put(copy.getVariable(), copy);
				for (CopyVarStatement copy : state.gen)
					state.out.put(copy.getVariable(), copy);

				if (!state.out.equals(oldState.out))
					changed = true;
				dataFlow.put(b, state);
			}
		}

		return dataFlow;
	}

	private HashMap<VarExpression, CopyVarStatement> meet(HashMap<VarExpression, CopyVarStatement> a, HashMap<VarExpression, CopyVarStatement> b) {
		HashMap<VarExpression, CopyVarStatement> result = new HashMap<>();
		HashSet<CopyVarStatement> copies = new HashSet<>(a.values());
		copies.addAll(b.values());
		for (CopyVarStatement copy : copies) {
			VarExpression var = copy.getVariable();
			if (a.containsKey(var) && b.containsKey(var)) {
				Expression rhsA = a.get(var).getExpression();
				Expression rhsB = b.get(var).getExpression();
				Expression rhs;
				if (rhsA == TOP_EXPR)
					rhs = rhsB;
				else if (rhsB == TOP_EXPR)
					rhs = rhsA;
				else if (rhsA == BOTTOM_EXPR || rhsB == BOTTOM_EXPR)
					rhs = BOTTOM_EXPR;
				else if (rhsA == rhsB)
					rhs = rhsA;
				else
					rhs = BOTTOM_EXPR;
				result.put(var, new CopyVarStatement(var, rhs));
			} else {
				result.put(var, new CopyVarStatement(var, TOP_EXPR));
			}
		}
		return result;
	}

	private DataFlowState computeFirstBlock() {
		DataFlowState state = compute(cfg.getEntry());
		for (CopyVarStatement copy : state.gen)
			state.out.put(copy.getVariable(), copy);
		return state;
	}

	private DataFlowState compute(BasicBlock b) {
		return new DataFlowState(computeGen(b), computeKill(b));
	}

	// GEN[b] = copies in b that reach end of block (no lhs or rhs redefinition)
	private HashSet<CopyVarStatement> computeGen(BasicBlock b) {
		HashMap<VarExpression, CopyVarStatement> gen = new HashMap<>();
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
		for (Statement stmt : b.getStatements()) {
			if (!(stmt instanceof CopyVarStatement))
				continue;
			CopyVarStatement newCopy = (CopyVarStatement) stmt;

			// Add all existing statements that would be overwritten by this
			for (CopyVarStatement copy : allCopies) {
				if (copy.getBlock() == b)
					continue;
				if (copy.getVariable().equals(newCopy.getVariable())) // check lhs
					kill.add(copy);
			}
		}

		return kill;
	}
}
