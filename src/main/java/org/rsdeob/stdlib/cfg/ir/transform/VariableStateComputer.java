package org.rsdeob.stdlib.cfg.ir.transform;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.edge.ConditionalJumpEdge;
import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.ConditionalJumpStatement;
import org.rsdeob.stdlib.cfg.ir.stat.ConditionalJumpStatement.ComparisonType;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.collections.NullPermeableHashMap;
import org.rsdeob.stdlib.collections.SetCreator;

public class VariableStateComputer {

	private final StatementGraph sgraph;
	private final Map<Statement, NullPermeableHashMap<String, Set<CopyVarStatement>>> in;
	private final Map<Statement, NullPermeableHashMap<String, Set<CopyVarStatement>>> out;
	// Entry<Statement, Calling predecessor>
	private final LinkedList<WorkListEntry> queue;
	private final Set<Statement> done;
	
	public VariableStateComputer(StatementGraph sgraph, MethodNode m) {
		this.sgraph = sgraph;
		in = new HashMap<>();
		out = new HashMap<>();
		queue = new LinkedList<>();
		done = new HashSet<>();
		
		populateTable();
		defineInputs(m);
		process();
	}
	
	public Map<String, Set<CopyVarStatement>> in(Statement stmt) {
		return in.get(stmt);
	}
	
	public Map<String, Set<CopyVarStatement>> out(Statement stmt) {
		return out.get(stmt);
	}

	private NullPermeableHashMap<String, Set<CopyVarStatement>> copy(NullPermeableHashMap<String, Set<CopyVarStatement>> map) {
		NullPermeableHashMap<String, Set<CopyVarStatement>> res = new NullPermeableHashMap<String, Set<CopyVarStatement>>(new SetCreator<>());
		for(Entry<String, Set<CopyVarStatement>> _e : map.entrySet()) {
			res.getNonNull(_e.getKey()).addAll(_e.getValue());
		}
		return res;
	}
	
	private void process() {
		while(!queue.isEmpty()) {
			WorkListEntry e = queue.pop();
			Statement stmt = e.stmt;

			// System.out.println("Processing " + e.edge + " , done=" + done.contains(stmt));
			
			if(done.contains(stmt)) {
				continue;
			}
			
			// first merge the state from the pred out into
			// the statement in
			NullPermeableHashMap<String, Set<CopyVarStatement>> oldStmtIn = in.get(stmt);
			NullPermeableHashMap<String, Set<CopyVarStatement>> newStmtIn = copy(oldStmtIn);
			
			mergeWithPred(newStmtIn, e.edge);
			
			NullPermeableHashMap<String, Set<CopyVarStatement>> newOut = propagate(stmt, newStmtIn);
			out.put(stmt, newOut);
			in.put(stmt, newStmtIn);
			
			if(equals(oldStmtIn, newStmtIn)) {
				done.add(stmt);
			}
		}
	}
	
	private void mergeWithPred(NullPermeableHashMap<String, Set<CopyVarStatement>> stmtIn, FlowEdge<Statement> edge) {
		Statement pred = edge.src;
		
		// i.e. it wasn't conditional
		// if(edge instanceof ImmediateEdge || edge instanceof UnconditionalJumpEdge || edge instanceof TryCatchEdge) {
			NullPermeableHashMap<String, Set<CopyVarStatement>> predOut = out.get(pred);
			for(Entry<String, Set<CopyVarStatement>> e : predOut.entrySet()) {
				stmtIn.getNonNull(e.getKey()).addAll(e.getValue());
			}
		// }
		
		if(pred instanceof ConditionalJumpStatement) {
			// the false jump edge is an immediate.
			boolean isTrueBranch = (edge instanceof ConditionalJumpEdge);
			ConditionalJumpStatement jump = (ConditionalJumpStatement) pred;
			Expression left = jump.getLeft();
			Expression right = jump.getRight();
			boolean isLeftVar = (left instanceof VarExpression);
			boolean isRightVar = (right instanceof VarExpression);
			// FIXME: what do we do if its a (varx == vary)
			if(isLeftVar && isRightVar) {
				return;
			} else if (!isLeftVar && !isRightVar) {
				return; // isn't a var check
			}
			
			VarExpression var = null;
			Expression other = null;
			if(isLeftVar) {
				var = (VarExpression) left;
				other = right;
			} else {
				// isRightVar must be true here
				var = (VarExpression) right;
				other = left;
			}
			
			String name = createVariableName(var);
			ComparisonType op = jump.getType();
			if(op == ComparisonType.EQ && isTrueBranch) {
				// A: if(x == 5) GOTO C
				// B:
				// C:     x must be 5 here (jump)
				stmtIn.getNonNull(name).add(new CopyVarStatement(var, other));
			} else if(op == ComparisonType.NE && !isTrueBranch) {
				// A: if(x != 5) GOTO C
				// B:     x must be 5 here (immediate)
				// C:
				stmtIn.getNonNull(name).add(new CopyVarStatement(var, other));
			}
			// TODO: represent >, <, >=, <= sets
		}
	}
	
	/* Calculates the variable state information after a statement
	 * is 'executed'. i.e. propagate the supplied in data to
	 * the same statements out data set. */
	private NullPermeableHashMap<String, Set<CopyVarStatement>> propagate(Statement stmt, NullPermeableHashMap<String, Set<CopyVarStatement>> inMap) {
		// first add all of the input variable states (i.e. assuming no change).
		NullPermeableHashMap<String, Set<CopyVarStatement>> newOut = copy(inMap);

		// next see if there is a definition.
		if(stmt instanceof CopyVarStatement) {
			CopyVarStatement copy = (CopyVarStatement) stmt;
			String name = createVariableName(copy);
			Set<CopyVarStatement> set = newOut.getNonNull(name);
			// the new expression overwrites the old definition, so
			// we remove all previous variable data in the set.
			set.clear();
			set.add(copy);
			
//			Expression rhs = copy.getExpression();
//			boolean contains = false;
//			for(CopyVarStatement cvs : set) {
//				Expression _rhs = cvs.getExpression();
//				if(rhs.equals(_rhs)) {
//					contains = true;
//				}
//			}
//			if(!contains) {
//				set.add(copy);
//			}
		}
		
		// lastly, add the successors to the work list.
		for(FlowEdge<Statement> fe : sgraph.getEdges(stmt)) {
			Statement succ = fe.dst;
			// WLEntry is considered from the perspective of
			//   the process queue:
			//       The stmt of the WLEntry is the next one, 
			//        i.e. succ of this
			//       The pred of the WLEntry is this statement
			queue.add(new WorkListEntry(succ, fe));
		}
		
		return newOut;
	}
	
	private boolean equals(NullPermeableHashMap<String, Set<CopyVarStatement>> map1, NullPermeableHashMap<String, Set<CopyVarStatement>> map2) {
		if(map1.size() != map2.size()) {
			return false;
		}
		
		Set<String> vars = new HashSet<>();
		vars.addAll(map1.keySet());
		vars.addAll(map2.keySet());
		
		for(String var : vars) {
			boolean contains1 = map1.containsKey(var);
			boolean contains2 = map2.containsKey(var);
			
			if(!contains1 && !contains2) {
				// ignore it since it is not
				// present in either set.
			} else if(contains1 && contains2) {
				// check match
				Set<CopyVarStatement> set1 = map1.get(var);
				Set<CopyVarStatement> set2 = map2.get(var);
				
				if(!set1.equals(set2)) {
					return false;
				}
			} else {
				// here either contains1 or
				// contains2 is false but not
				// both.
				return false;
			}
		}
		
		return true;
	}
	
	private void populateTable() {
		for(Statement stmt : sgraph.vertices()) {
			in.put(stmt, new NullPermeableHashMap<>(new SetCreator<>()));
			out.put(stmt, new NullPermeableHashMap<>(new SetCreator<>()));
		}
	}
	
	private void defineInputs(MethodNode m) {
		// build the entry in sets
		Type[] args = Type.getArgumentTypes(m.desc);
		int index = 0;
		if((m.access & Opcodes.ACC_STATIC) == 0) {
			addEntry(index, Type.getType(m.owner.name));
			index++;
		}
	
		for(int i=0; i < args.length; i++) {
			Type arg = args[i];
			addEntry(index, arg);
			index += arg.getSize();
		}
		
		// propagate entries
		for(Statement entry : sgraph.getEntries()) {
			out.put(entry, propagate(entry, in.get(entry)));
		}
	}
	
	private void addEntry(int index, Type type) {
		CopyVarStatement stmt = selfDefine(new VarExpression(index, type));
		String name = createVariableName(stmt);
		for(Statement entry : sgraph.getEntries()) {
			in.get(entry).getNonNull(name).add(stmt);
		}
	}
	
	public static String createVariableName(CopyVarStatement stmt) {
		VarExpression var = stmt.getVariable();
		return (var.isStackVariable() ? "s" : "l") + "var" + var.getIndex();
	}
	
	public static String createVariableName(VarExpression var) {
		return (var.isStackVariable() ? "s" : "l") + "var" + var.getIndex();
	}
	
	private CopyVarStatement selfDefine(VarExpression var) {
		return new CopyVarStatement(var, var);
	}
	
	private static class WorkListEntry {
		private final Statement stmt;
		private final FlowEdge<Statement> edge;
		public WorkListEntry(Statement stmt, FlowEdge<Statement> edge) {
			this.stmt = stmt;
			this.edge = edge;
		}
	}
}