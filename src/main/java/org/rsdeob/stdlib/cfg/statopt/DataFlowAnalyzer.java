package org.rsdeob.stdlib.cfg.statopt;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.FlowEdge;
import org.rsdeob.stdlib.cfg.expr.StackLoadExpression;
import org.rsdeob.stdlib.cfg.stat.StackDumpStatement;
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
			HashSet<Assignment> out = getAllCopiesExcluding(b);
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
				HashMap<Variable, Assignment> oldOut = new HashMap<>(state.out);
				// IN[b] = MEET(OUT[p] for p in predicates(b))
				HashMap<Variable, Assignment> in = state.in;
				for (FlowEdge e : b.getPredecessors()) {
					HashMap<Variable, Assignment> outP = dataFlow.get(e.src).out;
					in = meet(in, outP);
				}
				state.in = in;

				// OUT[b] = GEN[b] MEET (IN[b] - KILL[b])
				HashMap<Variable, Assignment> temp = new HashMap<>(state.in);
				for (Assignment copy : state.kill)
					temp.remove(copy.getVariable());
				state.out = meet(state.getGen(), temp);

				if (!state.out.equals(oldOut))
					changed = true;
			}
		}

		return dataFlow;
	}

	private HashMap<Variable, Assignment> meet(HashMap<Variable, Assignment> a, HashMap<Variable, Assignment> b) {
		HashMap<Variable, Assignment> result = new HashMap<>();
		HashSet<Assignment> copies = new HashSet<>(a.values());
		copies.addAll(b.values());
		for (Assignment copy : copies) {
			Variable var = copy.getVariable();
			if (a.containsKey(var) && b.containsKey(var)) {
				Statement rhsA = copy.getStatement();
				Statement rhsB = b.get(var).getStatement();
				Statement rhs;
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
				result.put(var, new Assignment(var, rhs));

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

	private HashSet<Assignment> getAllCopiesExcluding(BasicBlock exclude) {
		HashSet<Assignment> allCopies = new HashSet<>();
		for (BasicBlock b : cfg.blocks()) {
			if (b == exclude)
				continue;

			for (Statement stmt : b.getStatements()) {
				if (stmt instanceof StackDumpStatement) {
					Assignment copy = new Assignment((StackDumpStatement) stmt);
					allCopies.add(copy);
				}
			}
		}
		return allCopies;
	}

	private DataFlowState compute(BasicBlock b) {
		return new DataFlowState(computeGen(b), computeKill(b));
	}

	// GEN[b] = copies in b that reach end of block (no lhs or rhs redefinition)
	private HashSet<Assignment> computeGen(BasicBlock b) {
		HashSet<Assignment> gen = new HashSet<>();
		for (Statement stmt : b.getStatements()) { // iterating backwards would probably be faster
			if (stmt instanceof StackDumpStatement) { // stack assign
				StackDumpStatement stackDump = (StackDumpStatement) stmt;
				Assignment newCopy = new Assignment(stackDump);

				// remove overwritten copies
				HashSet<Assignment> toRemove = new HashSet<>();
				for (Assignment copy : gen) {
					if (copy.getVariable().equals(newCopy.getVariable())) { // check lhs
						toRemove.add(copy);
						break;
					}
					if (copy.getStatement() instanceof StackLoadExpression) { // check rhs
						if ((new Variable((StackLoadExpression) copy.getStatement())).equals(newCopy.getVariable())) {
							toRemove.add(copy);
						}
					}
				}
				gen.removeAll(toRemove);

				gen.add(newCopy);
			}
		}

		return gen;
	}

	// KILL[b] = all copies anywhere in the cfg that do not have lhs/rhs redefined in b
	private HashSet<Assignment> computeKill(BasicBlock b) {
		HashSet<Assignment> kill = new HashSet<>();
		for (Statement stmt : b.getStatements()) {
			if (stmt instanceof StackDumpStatement) {
				StackDumpStatement stackDump = (StackDumpStatement) stmt;
				Assignment newCopy = new Assignment(stackDump);

				// Add all existing statements that would be overwritten by this
				HashSet<Assignment> allCopies = getAllCopiesExcluding(b);
				for (Assignment copy : allCopies) {
					if (copy.getVariable().equals(newCopy.getVariable())) { // check lhs
						kill.add(copy);
					}
					if (copy.getStatement() instanceof StackLoadExpression) { // check rhs. TODO: can we just make SLE/Variable and SDS/Assignment the same please
						if ((new Variable((StackLoadExpression) copy.getStatement())).equals(newCopy.getVariable())) {
							kill.add(copy);
						}
					}
				}
			}
		}

		return kill;
	}
}
