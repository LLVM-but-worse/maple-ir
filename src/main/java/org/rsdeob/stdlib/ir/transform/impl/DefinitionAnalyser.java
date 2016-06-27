package org.rsdeob.stdlib.ir.transform.impl;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.collections.NullPermeableHashMap;
import org.rsdeob.stdlib.collections.SetCreator;
import org.rsdeob.stdlib.ir.Local;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.stat.SyntheticStatement;
import org.rsdeob.stdlib.ir.transform.ForwardsFlowAnalyser;

public class DefinitionAnalyser extends ForwardsFlowAnalyser<Statement, FlowEdge<Statement>, NullPermeableHashMap<Local, Set<CopyVarStatement>>> {
	
	public DefinitionAnalyser(StatementGraph graph, MethodNode m) {
		super(graph);
	}
	
	@Override
	public void remove(Statement n) {
		super.remove(n);
		
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
	
	@Override
	protected NullPermeableHashMap<Local, Set<CopyVarStatement>> newState() {
		return new NullPermeableHashMap<>(new SetCreator<>());
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

	@Override
	protected void propagate(Statement n, NullPermeableHashMap<Local, Set<CopyVarStatement>> in, NullPermeableHashMap<Local, Set<CopyVarStatement>> out) {
//		System.out.println("propagating " + n);
		// create a new set here because if we don't, future operations will
		// affect the in and the out sets. basically don't use out.putAll(in) here.
		for(Entry<Local, Set<CopyVarStatement>> e : in.entrySet()) {
			out.put(e.getKey(), new HashSet<>(e.getValue()));
		}
				
		if(n instanceof SyntheticStatement) {
			n = ((SyntheticStatement) n).getStatement();
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

	public Set<Statement> getUses(CopyVarStatement d) {
		HashSet<Statement> uses = new HashSet<>();
		for (Map.Entry<Statement, NullPermeableHashMap<Local, Set<CopyVarStatement>>> entry : in.entrySet())
			if (entry.getValue().get(d.getVariable().getLocal()).contains(d))
				uses.add(entry.getKey());
		return uses;
	}
}