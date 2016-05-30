package org.rsdeob.stdlib.cfg.ir.transform.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.ir.StatementVisitor;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.ir.transform.BackwardsFlowAnalyser;
import org.rsdeob.stdlib.collections.NullPermeableHashMap;
import org.rsdeob.stdlib.collections.SetCreator;
import org.rsdeob.stdlib.collections.graph.flow.FlowGraph;

public class LivenessAnalyser extends BackwardsFlowAnalyser<Statement, FlowEdge<Statement>, Map<String, Boolean>> {

	/* live(x, b) where x is a variable and b is a block
	 *   if (OR live(x, p)) where p are the predecessors of b.
	 *   
	 * ... := f(x) , x is live before this statement
	 * 
	 * x := e      , x is dead before this statement
	 * 
	 * y := z      , x is not referenced, so the in and
	 *               out liveness of x is the same.
	 * 
	 * algorithm:
	 *     live(x0...xn) = false;
	 */
	
	private final Map<String, Boolean> initial;
	private final NullPermeableHashMap<Statement, Set<VarExpression>> uses;
	
	public LivenessAnalyser(FlowGraph<Statement, FlowEdge<Statement>> graph) {
		super(graph);
		
		initial = new HashMap<>();
		uses = new NullPermeableHashMap<>(new SetCreator<>());
		for(Statement stmt : graph.vertices()) {
			StatementVisitor vis = new StatementVisitor(stmt) {
				@Override
				public Statement visit(Statement s) {
					if(s instanceof VarExpression) {
						initial.put(s.toString(), Boolean.valueOf(false));
						uses.getNonNull(stmt).add((VarExpression)s);
					} else if(s instanceof CopyVarStatement) {
						VarExpression var = ((CopyVarStatement) s).getVariable();
						initial.put(var.toString(), Boolean.valueOf(false));
						uses.getNonNull(stmt).add(var);
					}
					return s;
				}
			};
			vis.visit();
		}
	}

	@Override
	protected Map<String, Boolean> newState() {
		return new HashMap<>(initial);
	}

	@Override
	protected Map<String, Boolean> newEntryState() {
		return new HashMap<>(initial);
	}
	@Override
	protected void copy(Map<String, Boolean> src, Map<String, Boolean> dst) {
		for(Entry<String, Boolean> e : src.entrySet()) {
			dst.put(e.getKey(), e.getValue());
		}
	}

	@Override
	protected void propagate(Statement n, Map<String, Boolean> in, Map<String, Boolean> out) {
		for(Entry<String, Boolean> e : in.entrySet()) {
			String key = e.getKey();
			if(out.containsKey(key)) {
				out.put(key, e.getValue().booleanValue() || out.get(key).booleanValue());
			} else {
				out.put(key, e.getValue());
			}
		}
		Set<VarExpression> vars = uses.get(n);
		if(vars != null && vars.size() > 0) {
			for(VarExpression var : vars) {
				out.put(var.toString(), true);
			}
		}
		if(n instanceof CopyVarStatement) {
			out.put(((CopyVarStatement) n).getVariable().toString(), false);
		}
	}

	@Override
	protected void merge(Map<String, Boolean> in1, Map<String, Boolean> in2, Map<String, Boolean> out) {
		Set<String> keys = new HashSet<>();
		keys.addAll(in1.keySet());
		keys.addAll(in2.keySet());
		
		for(String key : keys) {
			if(in1.containsKey(key) && in2.containsKey(key)) {
				out.put(key, in1.get(key) || in2.get(key));
			} else if(!in1.containsKey(key)) {
				out.put(key, in2.get(key));
			} else if(!in2.containsKey(key)) {
				out.put(key, in1.get(key));
			}
		}
	}

	@Override
	protected boolean equals(Map<String, Boolean> s1, Map<String, Boolean> s2) {
		Set<String> keys = new HashSet<>();
		keys.addAll(s1.keySet());
		keys.addAll(s2.keySet());
		
		for(String key : keys) {
			if(!s1.containsKey(key) || !s2.containsKey(key)) {
				return false;
			}
			
			if(s1.get(key).booleanValue() != s2.get(key).booleanValue()) {
				return false;
			}
		}
		return true;
	}
}