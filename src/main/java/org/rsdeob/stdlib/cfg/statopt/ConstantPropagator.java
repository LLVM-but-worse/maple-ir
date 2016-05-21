package org.rsdeob.stdlib.cfg.statopt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.cfg.RootStatement;
import org.rsdeob.stdlib.cfg.StatementVisitor;
import org.rsdeob.stdlib.cfg.expr.StackLoadExpression;
import org.rsdeob.stdlib.cfg.stat.StackDumpStatement;
import org.rsdeob.stdlib.cfg.stat.Statement;

public class ConstantPropagator {

	private final ControlFlowGraph cfg;
	private final RootStatement root;
	private final Set<String> variables;
	private final Map<Statement, Set<String>> killSets;
	private final Map<Statement, Set<String>> genSets;
	private final Map<Statement, Set<Variable>> in;
	private final Map<Statement, Set<Variable>> out;
	
	public ConstantPropagator(ControlFlowGraph cfg){
		this.cfg = cfg;
		root = cfg.getRoot();
		killSets = new HashMap<>();
		genSets  = new HashMap<>();
		in = new HashMap<>();
		out  = new HashMap<>();
		variables = new HashSet<>();
		
		for(Statement stmt : getAllStatements()) {
			killSets.put(stmt, new HashSet<>());
			genSets.put(stmt, new HashSet<>());
			in.put(stmt, new HashSet<>());
			out.put(stmt, new HashSet<>());
		}
		
		computeSets();
	}
	
	public Set<Variable> getIn(Statement stmt) {
		return in.get(stmt);
	}
	
	public Set<Variable> getOut(Statement stmt) {
		return out.get(stmt);
	}
	
	List<Statement> getAllStatements() {
		List<Statement> stmts = new ArrayList<>();
		for(BasicBlock b : cfg.blocks()) {
			for(Statement stmt : b.getStatements()) {
				stmts.add(stmt);
			}	
		}
		return stmts;
	}
	
	Set<String> kill(Statement stmt) {
		return new HashSet<>(killSets.get(stmt));
	}
	
	Set<String> gen(Statement stmt) {
		return new HashSet<>(genSets.get(stmt));
	}
	
	Set<Variable> calcIn(Statement stmt) {
		Set<Variable> set = new HashSet<>();
		for(Statement pred : stmt.getPredecessors()) {
			if(pred != stmt) {
				set.addAll(calcOut(stmt));
			}
		}
		return set;
	}
	
	Set<Variable> calcOut(Statement stmt) {
		Set<Variable> set = new HashSet<>();
		set.addAll(calcIn(stmt));
		set.removeAll(kill(stmt));
		set.addAll(gen(stmt));
		return set;
	}
	
	public void compute() {
		List<Statement> stmts = getAllStatements();
		Statement entry = stmts.get(0);

		Set<Variable> eouts = new HashSet<>();
		for(String var : variables) {
			eouts.add(new Variable(var, null));
		}
		out.put(entry, eouts);
		
		LinkedList<Statement> worklist = new LinkedList<>();
		worklist.addAll(stmts);
		
		while(!worklist.isEmpty()) {
			Statement stmt = worklist.pop();
			Set<Variable> oldIn = in.get(stmt);
			Set<Variable> oldOut = out.get(stmt);
			
			Set<Variable> newIn = calcIn(stmt);
			Set<Variable> newOut = calcOut(stmt);
			
			if(!oldIn.equals(newIn) || !oldOut.equals(newOut)) {
				worklist.add(stmt);
			}
		}
	}
	
	void computeSets() {
		for (Statement stmt : getAllStatements()) {
			Set<String> kill = killSets.get(stmt);
			Set<String> gen = genSets.get(stmt);

			if (stmt instanceof StackDumpStatement) {
				StackDumpStatement dStmt = (StackDumpStatement) stmt;
				String name = createVarName(dStmt.getIndex(), dStmt.isStackVariable());
				variables.add(name);
				kill.add(name);
			}

			StatementVisitor visitor = new StatementVisitor(stmt) {
				@Override
				public void visit(Statement c) {
					if (c instanceof StackLoadExpression) {
						StackLoadExpression e = (StackLoadExpression) c;
						String name = createVarName(e.getIndex(), e.isStackVariable());
						variables.add(name);
						gen.add(name);
					}
				}
			};
			visitor.visit();

			kill.removeAll(gen);
		}
	}
	
	static String createVarName(int index, boolean stack) {
		return (stack ? "s" : "l") + index;
	}
	
	public static class Variable {
		private final String name;
		private final Statement stmt;
		
		public Variable(String name, Statement stmt) {
			this.name = name;
			this.stmt = stmt;
		}
		
		public Statement getStatement() {
			return stmt;
		}
		
		@Override
		public String toString() {
			return name;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Variable other = (Variable) obj;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			return true;
		}
	}
}