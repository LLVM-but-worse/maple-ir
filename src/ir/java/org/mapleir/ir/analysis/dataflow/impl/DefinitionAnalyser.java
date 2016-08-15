package org.mapleir.ir.analysis.dataflow.impl;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.mapleir.ir.analysis.StatementGraph;
import org.mapleir.ir.analysis.dataflow.ForwardsFlowAnalyser;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.CopyVarStatement;
import org.mapleir.ir.locals.Local;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.ir.StatementVisitor;

import java.util.Set;

public class DefinitionAnalyser extends ForwardsFlowAnalyser<Statement, FlowEdge<Statement>, NullPermeableHashMap<Local, Set<CopyVarStatement>>> {

	private NullPermeableHashMap<Statement, Set<Local>> uses;

	public DefinitionAnalyser(StatementGraph graph) {
		super(graph);
	}

	@Override
	public void init() {
		uses = new NullPermeableHashMap<>(new SetCreator<>());
		super.init();
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

	protected void merge(Statement nIn1, NullPermeableHashMap<Local, Set<CopyVarStatement>> in1, Statement nIn2, NullPermeableHashMap<Local, Set<CopyVarStatement>> in2, NullPermeableHashMap<Local, Set<CopyVarStatement>> out) {
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

		if(n instanceof CopyVarStatement) {
			CopyVarStatement stmt = (CopyVarStatement) n;
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
	protected void flowThrough(Statement srcS, NullPermeableHashMap<Local, Set<CopyVarStatement>> src, Statement dstS, NullPermeableHashMap<Local, Set<CopyVarStatement>> dst) {
		for(Entry<Local, Set<CopyVarStatement>> e : src.entrySet()) {
			Local local = e.getKey();
			if(!local.isStack()) {
				// on exception jump, stack is discarded.
				dst.put(local, new HashSet<>(e.getValue()));
			}
		}
	}
}