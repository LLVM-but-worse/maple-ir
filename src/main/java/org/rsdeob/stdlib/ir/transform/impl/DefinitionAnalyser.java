package org.rsdeob.stdlib.ir.transform.impl;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.collections.NullPermeableHashMap;
import org.rsdeob.stdlib.collections.SetCreator;
import org.rsdeob.stdlib.collections.graph.flow.ExceptionRange;
import org.rsdeob.stdlib.ir.Local;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.StatementVisitor;
import org.rsdeob.stdlib.ir.expr.VarExpression;
import org.rsdeob.stdlib.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.stat.SyntheticStatement;
import org.rsdeob.stdlib.ir.transform.ForwardsFlowAnalyser;

import java.util.*;
import java.util.Map.Entry;

public class DefinitionAnalyser extends ForwardsFlowAnalyser<Statement, FlowEdge<Statement>, NullPermeableHashMap<Local, Set<CopyVarStatement>>> {
	
	private Map<CopyVarStatement, SyntheticStatement> synth;
	private NullPermeableHashMap<Statement, Set<Local>> uses;
	
	public DefinitionAnalyser(StatementGraph graph) {
		super(graph);
	}

	@Override
	public void init() {
		synth = new HashMap<>();
		uses = new NullPermeableHashMap<>(new SetCreator<>());
		super.init();
	}
	
	@Override
	protected boolean queue(Statement n, boolean reset) {
		boolean isHandler = false;
		handlerCheck:
		for (ExceptionRange<Statement> range : graph.getRanges()) {
			Set<Statement> handlerStmts = graph.wanderAllTrails(range.getHandler(), n);
			for (FlowEdge<Statement> re : graph.getReverseEdges(n)) {
				if (handlerStmts.contains(re.src)) {
					isHandler = true;
					break handlerCheck;
				}
			}
		}
		System.out.println(n.getId() + ". Is handler: " + isHandler);

		boolean b = false;
		Set<Local> spreadLocals = new HashSet<>(uses.get(n));
		if (n instanceof CopyVarStatement)
			spreadLocals.add(((CopyVarStatement) n).getVariable().getLocal());
		for(Local l : spreadLocals) {
			if (!in(n).containsKey(l))
				continue;
			Set<CopyVarStatement> defs = in(n).get(l);
			for(CopyVarStatement def : defs) {
				Statement from = def;
				if(synth.containsKey(from)) {
					from = synth.get(from);
				}
				Set<Statement> path = graph.wanderAllTrails(from, n, isHandler && !l.isStack());
				path.remove(from); // loop fix
				for(Statement u : path) {
					appendQueue(u);
					if(reset) reset(u);
					b = true;
				}
			}
		}
		return b;
}
	
	@Override
	public void remove(Statement n) {
		super.remove(n);
		
		if(n instanceof SyntheticStatement || synth.get(n) != null) {
			throw new UnsupportedOperationException(n.toString() + ", type: " + n.getClass().getCanonicalName());
		}
		
		if(n instanceof CopyVarStatement) {
			CopyVarStatement cvs = (CopyVarStatement) n;

			for(Statement s : in.keySet()) {
				NullPermeableHashMap<Local, Set<CopyVarStatement>> in1 = in(s);
				for(Set<CopyVarStatement> set : in1.values()) {
					set.remove(cvs);
				}
			}
			for(Statement s : out.keySet()) {
				NullPermeableHashMap<Local, Set<CopyVarStatement>> out1 = out(s);
				for(Set<CopyVarStatement> set : out1.values()) {
					set.remove(cvs);
				}
			}
		}
	}
	
	@SuppressWarnings("serial")
	@Override
	protected NullPermeableHashMap<Local, Set<CopyVarStatement>> newState() {
		return new NullPermeableHashMap<Local, Set<CopyVarStatement>>(new SetCreator<>()) {
			@Override
			public String toString() {
				StringBuilder sb= new StringBuilder();
				sb.append("{");
				Iterator<Entry<Local, Set<CopyVarStatement>>> it = entrySet().iterator();
				while(it.hasNext()) {
					Entry<Local, Set<CopyVarStatement>> e = it.next();
					sb.append("  ").append(e.getKey()).append(" = ").append(e.getValue());
					if(it.hasNext()) {
						sb.append("\n");
					}
				}
				sb.append(" }");
				return sb.toString();
			}
		};
	}

	@Override
	protected NullPermeableHashMap<Local, Set<CopyVarStatement>> newEntryState() {
		return newState();
	}

	@Override
	protected void merge(NullPermeableHashMap<Local, Set<CopyVarStatement>> in1, NullPermeableHashMap<Local, Set<CopyVarStatement>> in2, NullPermeableHashMap<Local, Set<CopyVarStatement>> out) {
		for(Entry<Local, Set<CopyVarStatement>> e : in1.entrySet()) {
			out.getNonNull(e.getKey()).addAll(e.getValue());
		}
		for(Entry<Local, Set<CopyVarStatement>> e : in2.entrySet()) {
			out.getNonNull(e.getKey()).addAll(e.getValue());
		}
	}

	@Override
	protected void copy(NullPermeableHashMap<Local, Set<CopyVarStatement>> src, NullPermeableHashMap<Local, Set<CopyVarStatement>> dst) {
		for(Entry<Local, Set<CopyVarStatement>> e : src.entrySet()) {
			dst.getNonNull(e.getKey()).addAll(e.getValue());
		}
	}

	@Override
	protected boolean equals(NullPermeableHashMap<Local, Set<CopyVarStatement>> s1, NullPermeableHashMap<Local, Set<CopyVarStatement>> s2) {
		Set<Local> keys = new HashSet<>();
		keys.addAll(s1.keySet());
		keys.addAll(s2.keySet());
		
		for(Local key : keys) {
			if(!s1.containsKey(key) || !s2.containsKey(key)) {
				return false;
			}
			
			Set<CopyVarStatement> set1 = s1.get(key);
			Set<CopyVarStatement> set2 = s2.get(key);
			
			if(!set1.equals(set2)) {
				return false;
			}
		}
		
		return true;
	}

	private Set<Local> collectUses(Statement n) {
		Set<Local> set = new HashSet<>();
		new StatementVisitor(n) {
			@Override
			public Statement visit(Statement stmt) {
				if(stmt instanceof VarExpression) {
					set.add(((VarExpression) stmt).getLocal());
				}
				return stmt;
			}
		}.visit();
		return set;
	}
	
	@Override
	protected void execute(Statement n, NullPermeableHashMap<Local, Set<CopyVarStatement>> in, NullPermeableHashMap<Local, Set<CopyVarStatement>> out) {
		Set<Local> oldSet = uses.getNonNull(n);
		oldSet.clear();
		oldSet.addAll(collectUses(n));
		
		//	System.out.println("propagating " + n);
		// create a new set here because if we don't, future operations will
		// affect the in and the out sets. basically don't use out.putAll(in) here.
		for(Entry<Local, Set<CopyVarStatement>> e : in.entrySet()) {
			out.put(e.getKey(), new HashSet<>(e.getValue()));
		}

		if(n instanceof SyntheticStatement) {
			Statement stmt = ((SyntheticStatement) n).getStatement();
			if(stmt instanceof CopyVarStatement) {
				synth.put((CopyVarStatement)stmt, (SyntheticStatement)n);
			}
			n = stmt;
		}
		
		if(n instanceof CopyVarStatement) {
			CopyVarStatement stmt = (CopyVarStatement) n;
//			System.out.println("mapping def " + stmt.getId() +"  " + stmt);
			Local local = stmt.getVariable().getLocal();
			Set<CopyVarStatement> set = out.get(local);
			if(set == null) {
				set = new HashSet<>();
				out.put(local, set);
			}
			set.clear();
			set.add(stmt);
		}
	}
	
	public String toString(Statement stmt, String indent) {
		StringBuilder sb = new StringBuilder();
		
		Map<Local, Set<CopyVarStatement>> in = in(stmt);
		Map<Local, Set<CopyVarStatement>> out = out(stmt);
		
		if(in != null) {
			sb.append(indent).append("IN:\n");
			for(Entry<Local, Set<CopyVarStatement>> e : in.entrySet()) {
				sb.append(indent).append("  ").append(e.getKey()).append(" = ").append(e.getValue()).append("\n");
			}
		}
		
		if(out != null) {
			sb.append(indent).append("OUT:\n");
			for(Entry<Local, Set<CopyVarStatement>> e : out.entrySet()) {
				sb.append(indent).append("  ").append(e.getKey()).append(" = ").append(e.getValue()).append("\n");
			}
		}
		
		if(in == null && out == null) {
			sb.append(indent).append("\n");
		}
		
		return sb.toString();
	}

	@Override
	protected void copyException(NullPermeableHashMap<Local, Set<CopyVarStatement>> src, NullPermeableHashMap<Local, Set<CopyVarStatement>> dst) {
		for(Entry<Local, Set<CopyVarStatement>> e : src.entrySet()) {
			Local local = e.getKey();
			if(!local.isStack()) {
				// on exception jump, stack is discarded.
				dst.put(local, new HashSet<>(e.getValue()));
			}
		}
	}
}