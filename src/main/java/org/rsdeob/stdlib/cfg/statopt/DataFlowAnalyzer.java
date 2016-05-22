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

	public DataFlowAnalyzer(ControlFlowGraph cfg) {
		this.cfg = cfg;
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
			HashSet<CopyVarStatement> out = getAllCopiesExcluding(b);
			out.removeAll(state.kill);
			state.copyToOut(out);
			dataFlow.put(b, state);
		}

		boolean changed = true;
		int i = 0;
		while (changed) {
			System.out.println("Iteration " + ++i);
			changed = false;
			for (BasicBlock b : cfg.blocks()) {
				if (b == cfg.getEntry())
					continue;

				DataFlowState state = dataFlow.get(b);
				HashMap<VarExpression, CopyVarStatement> oldOut = new HashMap<>(state.out);
				// IN[b] = MEET(OUT[p] for p in predicates(b))
				HashMap<VarExpression, CopyVarStatement> in = state.in;
				for (FlowEdge e : b.getPredecessors()) {
					HashMap<VarExpression, CopyVarStatement> outP = dataFlow.get(e.src).out;
					in = meet(in, outP);
				}
				state.in = in;

				// OUT[b] = GEN[b] MEET (IN[b] - KILL[b])
				HashMap<VarExpression, CopyVarStatement> temp = new HashMap<>(state.in);
				for (CopyVarStatement copy : state.kill)
					temp.remove(copy.getVariable());
				state.out = meet(state.getGen(), temp);

				if (!state.out.equals(oldOut))
					changed = true;
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
				Expression rhsA = copy.getExpression();
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
					rhs = TOP_EXPR;
				result.put(var, new CopyVarStatement(var, rhs));

			} else {
				result.put(var, copy);
			}
		}
		return result;
	}

	private DataFlowState computeFirstBlock() {
		DataFlowState state = compute(cfg.getEntry());
		state.copyToOut(state.gen);
		return state;
	}

	private HashSet<CopyVarStatement> getAllCopiesExcluding(BasicBlock exclude) {
		HashSet<CopyVarStatement> allCopies = new HashSet<>();
		for (BasicBlock b : cfg.blocks()) {
			if (b == exclude)
				continue;

			for (Statement stmt : b.getStatements()) {
				if (stmt instanceof CopyVarStatement) {
					allCopies.add((CopyVarStatement) stmt);
				}
			}
		}
		return allCopies;
	}

	private DataFlowState compute(BasicBlock b) {
		return new DataFlowState(computeGen(b), computeKill(b));
	}

	// GEN[b] = copies in b that reach end of block (no lhs or rhs redefinition)
	private HashSet<CopyVarStatement> computeGen(BasicBlock b) {
		HashSet<CopyVarStatement> gen = new HashSet<>();
		for (Statement stmt : b.getStatements()) { // iterating backwards would probably be faster
			if (stmt instanceof CopyVarStatement) { // stack assign
				CopyVarStatement newCopy = (CopyVarStatement) stmt;

				// remove overwritten copies
				HashSet<CopyVarStatement> toRemove = new HashSet<>();
				for (CopyVarStatement copy : gen) {
					if (copy.getVariable().equals(newCopy.getVariable())) { // check lhs
						toRemove.add(copy);
						break;
					}
					if (copy.getExpression().equals(newCopy.getVariable())) { // check rhs
						toRemove.add(copy);
					}
				}
				gen.removeAll(toRemove);

				gen.add(newCopy);
			}
		}

		return gen;
	}

	// KILL[b] = all copies anywhere in the cfg that do not have lhs/rhs redefined in b
	private HashSet<CopyVarStatement> computeKill(BasicBlock b) {
		HashSet<CopyVarStatement> kill = new HashSet<>();
		for (Statement stmt : b.getStatements()) {
			if (stmt instanceof CopyVarStatement) {
				CopyVarStatement newCopy = (CopyVarStatement) stmt;

				// Add all existing statements that would be overwritten by this
				HashSet<CopyVarStatement> allCopies = getAllCopiesExcluding(b);
				for (CopyVarStatement copy : allCopies) {
					if (copy.getVariable().equals(newCopy.getVariable())) { // check lhs
						kill.add(copy);
					}
					if (copy.getExpression().equals(newCopy.getVariable())) { // check rhs
						kill.add(copy);
					}
				}
			}
		}

		return kill;
	}
}
