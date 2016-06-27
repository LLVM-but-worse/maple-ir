package org.rsdeob.stdlib.ir.transform.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.collections.NullPermeableHashMap;
import org.rsdeob.stdlib.collections.SetCreator;
import org.rsdeob.stdlib.collections.graph.flow.FlowGraph;
import org.rsdeob.stdlib.ir.Local;
import org.rsdeob.stdlib.ir.StatementVisitor;
import org.rsdeob.stdlib.ir.expr.VarExpression;
import org.rsdeob.stdlib.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.transform.BackwardsFlowAnalyser;

public class LivenessAnalyser extends BackwardsFlowAnalyser<Statement, FlowEdge<Statement>, Map<Local, Boolean>> {

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
	
	private Map<Local, Boolean> initial;
	private NullPermeableHashMap<Statement, Set<Local>> uses;
	
	public LivenessAnalyser(FlowGraph<Statement, FlowEdge<Statement>> graph) {
		super(graph);
	}
	
	@Override
	protected void init() {
		
		initial = new HashMap<>();
		uses = new NullPermeableHashMap<>(new SetCreator<>());
		for(Statement stmt : graph.vertices()) {
			if(stmt instanceof CopyVarStatement) {
				VarExpression var = ((CopyVarStatement) stmt).getVariable();
				initial.put(var.getLocal(), Boolean.valueOf(false));
			}
			
			StatementVisitor vis = new StatementVisitor(stmt) {
				@Override
				public Statement visit(Statement s) {
					if(s instanceof VarExpression) {
						Local var = ((VarExpression) s).getLocal();
						initial.put(var, Boolean.valueOf(false));
						uses.getNonNull(stmt).add(var);
					}
					return s;
				}
			};
			vis.visit();
		}
		
		super.init();
	}

	@Override
	public void remove(Statement n) {
		super.remove(n);
		uses.remove(n);
		initial.remove(n);
	}
	
	@Override
	public void replaceImpl(Statement old, Statement n) {
		super.replaceImpl(old, n);
		
		if(uses.containsKey(old)) {
			uses.remove(old);
			
			StatementVisitor vis = new StatementVisitor(n) {
				@Override
				public Statement visit(Statement s) {
					if(s instanceof VarExpression) {
						Local var = ((VarExpression) s).getLocal();
						uses.getNonNull(n).add(var);
					}
					return s;
				}
			};
			vis.visit();
		}
	}
	
	@Override
	protected Map<Local, Boolean> newState() {
		return new HashMap<>(initial);
	}

	@Override
	protected Map<Local, Boolean> newEntryState() {
		return new HashMap<>(initial);
	}
	
	@Override
	protected void copy(Map<Local, Boolean> src, Map<Local, Boolean> dst) {
		for(Entry<Local, Boolean> e : src.entrySet()) {
			dst.put(e.getKey(), e.getValue());
		}
	}

	@Override
	protected void propagate(Statement n, Map<Local, Boolean> in, Map<Local, Boolean> out) {
		// System.out.println("propagating " + n);
		
		for(Entry<Local, Boolean> e : in.entrySet()) {
			Local key = e.getKey();
			if(out.containsKey(key)) {
				out.put(key, e.getValue().booleanValue() || out.get(key).booleanValue());
			} else {
				out.put(key, e.getValue());
			}
			// System.out.println("   " + key + " is " + (e.getValue() ? "live" : "dead"));
		}
		// do this before the uses because of
		// varx = varx statements (i had a dream about
		// it trust me it works).

		if(n instanceof CopyVarStatement) {
			out.put(((CopyVarStatement) n).getVariable().getLocal(), false);
		}
		
		Set<Local> vars = uses.get(n);
		if(vars != null && vars.size() > 0) {
			for(Local var : vars) {
				out.put(var, true);
			}
		}
	}

	@Override
	protected void merge(Map<Local, Boolean> in1, Map<Local, Boolean> in2, Map<Local, Boolean> out) {
		Set<Local> keys = new HashSet<>();
		keys.addAll(in1.keySet());
		keys.addAll(in2.keySet());
		
		for(Local key : keys) {
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
	protected boolean equals(Map<Local, Boolean> s1, Map<Local, Boolean> s2) {
		Set<Local> keys = new HashSet<>();
		keys.addAll(s1.keySet());
		keys.addAll(s2.keySet());
		
		for(Local key : keys) {
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