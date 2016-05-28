package org.rsdeob.stdlib.cfg.ir.transform;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;

public class BiblPropagator {

	private final StatementGraph sgraph;
	private final Set<CopyVarStatement> copies;
	private final Map<Statement, Set<CopyVarStatement>> gen;
	private final Map<Statement, Set<CopyVarStatement>> kill;
	private final Map<Statement, Set<Statement>> in;
	private final Map<Statement, Set<Statement>> out;
	
	public BiblPropagator(StatementGraph sgraph, MethodNode m) {
		this.sgraph = sgraph;
		in = new HashMap<>();
		out = new HashMap<>();
		gen = new HashMap<>();
		kill = new HashMap<>();
		copies = new HashSet<>();
		
		init();
		calcInitial(Type.getArgumentTypes(m.desc));
		calc();
	}
	
	public Set<Statement> in(Statement stmt) {
		return in.get(stmt);
	}
	
	public Set<Statement> out(Statement stmt) {
		return out.get(stmt);
	}
	
	void calc() {
		while(true) {
			boolean change = false;
			
			for(Statement stmt : sgraph.vertices()) {
				Set<Statement> oldIn = in.get(stmt);
				Set<Statement> oldOut = out.get(stmt);
				
				Set<Statement> inSet = calcIn(stmt);
				Set<Statement> outSet = calcOut(stmt, inSet);
				
				if(!oldIn.equals(inSet) || !oldOut.equals(outSet)) {
					in.put(stmt, inSet);
					out.put(stmt, outSet);
					change = true;
				}
			}
			
			if(!change) {
				break;
			}
		}
	}
	
	void calcInitial(Type[] args) {
		// in set for the method. this can also be
		// thought of as the inputs to the method
		Set<Statement> methodIn = new HashSet<>();
		int index = 0;
		for(int i=0; i < args.length; i++) {
			Type arg = args[i];
			// create a copy statement where
			//   x := x;
			// as a dummy statement to act as
			// an input.
			VarExpression var = new VarExpression(index, arg);
			VarExpression expr = new VarExpression(index, arg);
			CopyVarStatement copyStmt = new CopyVarStatement(var, expr);
			methodIn.add(copyStmt);
			index += arg.getSize();
		}
		
		Set<Statement> entries = sgraph.getEntries();
		System.out.println("ntries: " + entries);
		System.out.println("min: " + methodIn);
		for(Statement stmt : sgraph.vertices()) {
			if(entries.contains(stmt)) {
				// build the out sets for the entry nodes, here
				// we have the methodIn statements as
				// the predecessors to the entries.
				Set<Statement> outSet = calcOut(stmt, methodIn);
				out.put(stmt, outSet);
			} else {
				// otherwise the initial out set is empty.
				out.put(stmt, new HashSet<>());
			}
			in.put(stmt, new HashSet<>());
		}
	}
	
	Set<Statement> calcIn(Statement stmt) {
		List<Statement> preds = stmt.getPredecessors();
		Set<Statement> outSet = new HashSet<>();
		ListIterator<Statement> lit = preds.listIterator();
		while(lit.hasNext()) {
			Statement pred = lit.next();
			outSet.addAll(out.get(pred));
		}
		return outSet;
	}
	
	Set<Statement> calcOut(Statement stmt, Set<Statement> in) {
		Set<Statement> res = new HashSet<>(in);
		res.removeAll(kill.get(stmt));
		res.addAll(gen.get(stmt));
		return res;
	}
	
	void init() {
		// calc copies and gen in 1 pass, then kill in another
		for(Statement stmt : sgraph.vertices()) {
			Set<CopyVarStatement> _gen = new HashSet<>();
			if(stmt instanceof CopyVarStatement) {
				CopyVarStatement copyStmt = (CopyVarStatement) stmt;
				copies.add(copyStmt);
				_gen.add(copyStmt);
			}
			gen.put(stmt, _gen);
		}
		
		for(Statement stmt : sgraph.vertices()) {
			Set<CopyVarStatement> _kill = new HashSet<>(copies);
			_kill.remove(gen.get(stmt));
			kill.put(stmt, _kill);
		}
	}
	
	private void calcEntry() {
		// first in
	}
	
	CopyVarStatement calcGen(Statement stmt) {
		if(stmt instanceof CopyVarStatement) {
			return (CopyVarStatement) stmt;
		} else {
			return null;
		}
	}
	
	void calcKill(Statement stmt) {
		
	}
	
//	public static abstract class Value {
//	}
//	
//	public static class LocalValue extends Value {
//		private final String var;
//		public LocalValue(String var) {
//			this.var = var;
//		}
//		@Override
//		public String toString() {
//			return var;
//		}
//		@Override
//		public int hashCode() {
//			return var.hashCode();
//		}
//	}
}