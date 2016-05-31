package org.rsdeob.stdlib.cfg.ir.transform.impl;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementVisitor;
import org.rsdeob.stdlib.cfg.ir.expr.ConstantExpression;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.ir.transform.ForwardsFlowAnalyser;
import org.rsdeob.stdlib.cfg.ir.transform.VariableStateComputer;
import org.rsdeob.stdlib.collections.NullPermeableHashMap;
import org.rsdeob.stdlib.collections.SetCreator;

public class TrackerImpl extends ForwardsFlowAnalyser<Statement, FlowEdge<Statement>, NullPermeableHashMap<String, Set<CopyVarStatement>>>{

	private final NullPermeableHashMap<String, Set<CopyVarStatement>> initial;
	
	public TrackerImpl(StatementGraph graph, MethodNode m) {
		super(graph);
		initial = newState();
		defineInputs(m);
	}
	
	public void propagate() {
		for(Statement stmt : graph.vertices()) {
			Map<String, Set<CopyVarStatement>> in = in(stmt);
			
			StatementVisitor impl = new StatementVisitor(stmt) {
				@Override
				public Statement visit(Statement s) {
					if(s instanceof VarExpression) {
						VarExpression var = (VarExpression) s;
						String name = VariableStateComputer.createVariableName(var);
						Set<CopyVarStatement> defs = in.get(name);
						
						if(defs != null && defs.size() == 1) {
							CopyVarStatement copy = defs.iterator().next();
							Expression def = copy.getExpression();
							if(def instanceof ConstantExpression || def instanceof VarExpression) {
								int d = getDepth();
								getCurrent(d).overwrite(def, getCurrentPtr(d));
								
							}
						}
					}
					return s;
				}
			};
			impl.visit();
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
	}
	
	private void addEntry(int index, Type type) {
		CopyVarStatement stmt = selfDefine(new VarExpression(index, type));
		String name = createVariableName(stmt);
		initial.getNonNull(name).add(stmt);
	}
	
	public static String createVariableName(CopyVarStatement stmt) {
		VarExpression var = stmt.getVariable();
		return (var.isStackVariable() ? "s" : "l") + "var" + var.getIndex();
	}
	
	private CopyVarStatement selfDefine(VarExpression var) {
		return new CopyVarStatement(var, var);
	}

	@Override
	protected NullPermeableHashMap<String, Set<CopyVarStatement>> newState() {
		return new NullPermeableHashMap<>(new SetCreator<>());
	}

	@Override
	protected NullPermeableHashMap<String, Set<CopyVarStatement>> newEntryState() {
		NullPermeableHashMap<String, Set<CopyVarStatement>> map = newState();
		copy(initial, map);
		return map;
	}

	@Override
	protected void merge(NullPermeableHashMap<String, Set<CopyVarStatement>> in1, NullPermeableHashMap<String, Set<CopyVarStatement>> in2, NullPermeableHashMap<String, Set<CopyVarStatement>> out) {
		out.putAll(in1);
		out.putAll(in2);
	}

	@Override
	protected void copy(NullPermeableHashMap<String, Set<CopyVarStatement>> src, NullPermeableHashMap<String, Set<CopyVarStatement>> dst) {
		for(Entry<String, Set<CopyVarStatement>> e : src.entrySet()) {
			dst.getNonNull(e.getKey()).addAll(e.getValue());
		}
	}

	@Override
	protected boolean equals(NullPermeableHashMap<String, Set<CopyVarStatement>> s1, NullPermeableHashMap<String, Set<CopyVarStatement>> s2) {
		Set<String> keys = new HashSet<>();
		keys.addAll(s1.keySet());
		keys.addAll(s2.keySet());
		
		for(String key : keys) {
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
	protected void propagate(Statement n, NullPermeableHashMap<String, Set<CopyVarStatement>> in, NullPermeableHashMap<String, Set<CopyVarStatement>> out) {
		// create a new set here because if we don't, future operations will
		// affect the in and the out sets. basically don't use out.putAll(in) here.
		for(Entry<String, Set<CopyVarStatement>> e : in.entrySet()) {
			out.put(e.getKey(), new HashSet<>(e.getValue()));
		}
		
		// final VarExpression rhs;
		
		if(n instanceof CopyVarStatement) {
			CopyVarStatement stmt = (CopyVarStatement) n;
			String name = VariableStateComputer.createVariableName(stmt);
			Set<CopyVarStatement> set = out.get(name);
			if(set == null) {
				set = new HashSet<>();
				out.put(name, set);
			}
			set.clear();
			set.add(stmt);
			// rhs = stmt.getVariable();
		} else {
			// rhs = null;
		}
	}
}