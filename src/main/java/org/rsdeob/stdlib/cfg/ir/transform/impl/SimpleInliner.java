package org.rsdeob.stdlib.cfg.ir.transform.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.ir.Local;
import org.rsdeob.stdlib.cfg.ir.RootStatement;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementVisitor;
import org.rsdeob.stdlib.cfg.ir.expr.FieldLoadExpression;
import org.rsdeob.stdlib.cfg.ir.expr.InvocationExpression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.ArrayStoreStatement;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.FieldStoreStatement;
import org.rsdeob.stdlib.cfg.ir.stat.MonitorStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;

public class SimpleInliner {

	private final RootStatement root;
	private final StatementGraph graph;
	private final DefinitionAnalyser definitions;
	private final UsesAnalyser usesAnalyser;
	
	public SimpleInliner(RootStatement root, StatementGraph graph, DefinitionAnalyser definitions, UsesAnalyser usesAnalyser) {
		this.root = root;
		this.graph = graph;
		this.definitions = definitions;
		this.usesAnalyser = usesAnalyser;
	}

	public int run() {
		Map<Statement, CopyVarStatement> replace = new HashMap<>();
		
		StatementVisitor vis = new StatementVisitor(root) {
			@Override
			public Statement visit(Statement s) {
				if(s instanceof CopyVarStatement) {
					CopyVarStatement def = (CopyVarStatement) s;
					Local local = def.getVariable().getLocal();
					Set<Statement> uses = usesAnalyser.getUses(def);
					if(uses == null || uses.size() != 1) {
						return s;
					}
					
					Statement use = uses.iterator().next();
					Set<VarExpression> usedSet = usesAnalyser.getVarsUsed(s);
					if(usedSet.size() != 1) {
						return s;
					}
					VarExpression var = usedSet.iterator().next();
					
					if(definitions.in(use).get(local).size() != 1) {
						return s;
					}
					
					System.out.println("usess of " + def +"  " + def.getId());
					System.out.println("    " + uses);
					
					
					AtomicBoolean fail = new AtomicBoolean();
					AtomicBoolean invoke = new AtomicBoolean();
					AtomicBoolean field = new AtomicBoolean();
					AtomicBoolean array = new AtomicBoolean();
					
					Set<Local> locals = new HashSet<>();
					Set<FieldLoadExpression> fields = new HashSet<>();
					
					StatementVisitor vis1 = new StatementVisitor(s) {
						@Override
						public Statement visit(Statement stmt) {
							if(stmt instanceof VarExpression) {
								locals.add(((VarExpression) stmt).getLocal());
							} else if(stmt instanceof InvocationExpression) {
								invoke.set(true);
							} else if(stmt instanceof FieldLoadExpression) {
								field.set(true);
								fields.add((FieldLoadExpression) stmt);
							}
							return stmt;
						}
					};
					vis1.visit();
					
					List<Statement> path = findPath(def, use);
					if(path == null) {
						return s;
					}
					
					Iterator<Statement> it = path.listIterator();
					if(it.hasNext())
						it.next();
					
					while(it.hasNext() && !fail.get()) {
						Statement i = it.next();
						
						if(i != use) {
							if(i instanceof CopyVarStatement) {
								CopyVarStatement copy = (CopyVarStatement) i;
								if(locals.contains(copy.getVariable().getLocal())) {
									fail.set(true);
								}
							}
							
							if(invoke.get() || field.get() || array.get()) {
								if(i instanceof FieldStoreStatement) {
									FieldStoreStatement store = (FieldStoreStatement) i;
									
									if(invoke.get()) {
										fail.set(true);
									} else if(field.get()) {
										for(FieldLoadExpression fl : fields) {
											if(fl.getName().equals(store.getName()) && fl.getDesc().equals(store.getDesc())) {
												fail.set(true);
											}
										}
									}
								} else if(i instanceof ArrayStoreStatement) {
									if(invoke.get()) {
										fail.set(true);
									} else if(array.get()) {
										// TODO: do more strict checking to let more stuff
										//       pass through.
									}
								}
							}
							
							if(invoke.get() && i instanceof MonitorStatement) {
								fail.set(true);
							}
						}
						
						if(invoke.get() && field.get() && array.get()) {
							StatementVisitor usesVis = new StatementVisitor(i) {
								@Override
								public Statement visit(Statement s1) {
									if(i == use && s1 == var) {
										_break();
										return s1;
									}
									
									if(s1 instanceof InvocationExpression || (invoke.get() && (s1 instanceof FieldLoadExpression || s1 instanceof ArrayStoreStatement))) {
										fail.set(true);
										_break();
										return s1;
									}
									return s1;
								}
							};
							usesVis.visit();
						}
					}
					
					if(fail.get()) {
						return s;
					}
					
					replace.put(var, def);
				}
				return s;
			}
		};
		vis.visit();
		
		StatementVisitor vis1 = new StatementVisitor(root) {
			@Override
			public Statement visit(Statement s) {
				if(replace.containsKey(s)) {
					CopyVarStatement def = replace.get(s);
					return def.getExpression().copy();
				}
				return s;
			}
		};
		vis1.visit();
		
		for(CopyVarStatement def : replace.values()) {
			root.delete(root.indexOf(def));
		}
		return 0;
	}
	
	private List<Statement> findPath(Statement s, Statement target) {
		if(graph.getReverseEdges(target).size() > 1) {
			return null;
		}
		
		List<Statement> stack = new ArrayList<>();
		List<Integer> indices = new ArrayList<>();
		
		stack.add(s);
		indices.add(Integer.valueOf(0));
		
		int max = graph.getEdges(stack.get(0)).size();
		int level = 0;
		
		while(indices.get(0).intValue() != max) {
			int p = indices.get(level).intValue();
			Set<FlowEdge<Statement>> edges = graph.getEdges(stack.get(level));
			if(p > edges.size()) {
				stack.remove(level);
				indices.remove(level--);
				
				int q = indices.get(level).intValue();
				indices.set(level, Integer.valueOf(q + 1));
			}
			
			List<Statement> succs = new ArrayList<>();
			for(FlowEdge<Statement> e : edges) {
				succs.add(e.dst);
			}
			
			Statement i = succs.get(p);
			
			if(i == target) {
				stack.add(target);
				return stack;
			}
			
			if(graph.getReverseEdges(i).size() > 1) {
				indices.set(level, Integer.valueOf(p + 1));
				continue;
			}
			
			level++;
			indices.add(Integer.valueOf(0));
			stack.add(i);
		}
		
		return null;
	}
}