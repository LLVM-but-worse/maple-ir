package org.rsdeob.stdlib.cfg.ir.transform.impl;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.edge.TryCatchEdge;
import org.rsdeob.stdlib.cfg.ir.Local;
import org.rsdeob.stdlib.cfg.ir.RootStatement;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementVisitor;
import org.rsdeob.stdlib.cfg.ir.expr.ArrayLoadExpression;
import org.rsdeob.stdlib.cfg.ir.expr.ConstantExpression;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.FieldLoadExpression;
import org.rsdeob.stdlib.cfg.ir.expr.InvocationExpression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.ArrayStoreStatement;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.FieldStoreStatement;
import org.rsdeob.stdlib.cfg.ir.stat.MonitorStatement;
import org.rsdeob.stdlib.cfg.ir.stat.PopStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.ir.stat.SyntheticStatement;

public class CopyPropagator {
	
	private final RootStatement root;
	private final StatementGraph graph;
	private final Map<Statement, SyntheticStatement> synthetics;
	private DefinitionAnalyser definitions;
	private LivenessAnalyser liveness;
	private UsesAnalyser uses;
	private int changedStmts;
	
	public CopyPropagator(RootStatement root, StatementGraph graph) {
		this.root = root;
		this.graph = graph;
		synthetics = new HashMap<>();
		
		for(int i=0; root.read(i) != null; i++) {
			Statement stmt = root.read(i);
			if(stmt instanceof SyntheticStatement) {
				synthetics.put(((SyntheticStatement) stmt).getStatement(), (SyntheticStatement) stmt);
			}
		}
	}
	
	private void processImpl() {
		changedStmts = 0;
		while(true) {
			AtomicBoolean change = new AtomicBoolean(false);
			
			List<Statement> list = new ArrayList<>(graph.vertices());
			Collections.sort(list, new Comparator<Statement>() {
				@Override
				public int compare(Statement o1, Statement o2) {
					return Long.compare(o1._getId(), o2._getId());
				}
			});
			
			for(Statement stmt : list) {
				if(stmt instanceof SyntheticStatement)
					continue;
				if(stmt instanceof PopStatement) {
					Expression expr = ((PopStatement) stmt).getExpression();
					if(expr instanceof ConstantExpression || expr instanceof VarExpression) {
						definitions.remove(stmt);
						liveness.remove(stmt);
						graph.excavate(stmt);
						root.delete(root.indexOf(stmt));
						definitions.processQueue();
						liveness.processQueue();
						uses.remove(stmt);
						continue;
					}
				}
				
				Transformer transformer = new Transformer(stmt);
				transformer.visit();
				
				if(transformer.change) {
					change.set(true);
					continue;
				}
			}
			
			if(!change.get()) {
				break;
			}
		}
	}
	
	public int process(DefinitionAnalyser definitions, UsesAnalyser uses, LivenessAnalyser liveness) {
		this.definitions = definitions;
		this.uses = uses;
		this.liveness = liveness;
		processImpl();
		return changedStmts;
	}
	
	private Expression transform(CopyVarStatement localDef, Statement use) {
		Statement real = localDef;
		if(synthetics.containsKey(localDef)) {
			real = synthetics.get(localDef);
		}
		
		Local local = localDef.getVariable().getLocal();
		Expression rhs = localDef.getExpression();
		
		// current scenario:
		//    var2 = rhs;
		//    ...
		//    use(var2);
		
		// here we go through rhs and collect
		// all types of variables that are used 
		// in the expression. this includes
		// VarExpressions, FieldLoadExpression,
		// ArrayLoadExpressions and InvokeExpressions.
		
		Set<Local> localsUsed = new HashSet<>();
		Set<String> fieldsUsed = new HashSet<>();
		AtomicBoolean invoke = new AtomicBoolean();
		AtomicBoolean array = new AtomicBoolean();
		
		{
			if(rhs instanceof VarExpression) {
				localsUsed.add(((VarExpression) rhs).getLocal());
			} else if(rhs instanceof FieldLoadExpression) {
				fieldsUsed.add(((FieldLoadExpression) rhs).getName() + "." + ((FieldLoadExpression) rhs).getDesc());
			} else if(rhs instanceof InvocationExpression) {
				invoke.set(true);
			} else if(rhs instanceof ArrayLoadExpression) {
				array.set(true);
			} else if(rhs instanceof ConstantExpression) {
				return rhs;
			}
		}
		
		StatementVisitor vis1 = new StatementVisitor(rhs) {
			@Override
			public Statement visit(Statement stmt) {
				if(stmt instanceof VarExpression) {
					localsUsed.add(((VarExpression) stmt).getLocal());
				} else if(stmt instanceof FieldLoadExpression) {
					fieldsUsed.add(((FieldLoadExpression) stmt).getName() + "." + ((FieldLoadExpression) stmt).getDesc());
				} else if(stmt instanceof InvocationExpression) {
					invoke.set(true);
				} else if(stmt instanceof ArrayLoadExpression) {
					array.set(true);
				}
				return stmt;
			}
		};
		vis1.visit();
		
		// now using these collected variables and
		// rules we can go through from def to rhs
		// to check if anything is being overwritten.
		
		Collection<Statement> path = findPossibleExecutedStatements(real, use);
		if(path == null) {
			return null;
		}
		
		boolean canPropagate = true;
		
		for(Statement stmt : path) {
			if(stmt != use) {
				if(stmt instanceof CopyVarStatement) {
					CopyVarStatement copy = (CopyVarStatement) stmt;
					if(localsUsed.contains(copy.getVariable().getLocal())) {
						canPropagate = false;
						break;
					}
				}
				
				if(stmt instanceof FieldStoreStatement) {
					if(invoke.get()) {
						canPropagate = false;
						break;
					} else if(fieldsUsed.size() > 0) {
						FieldStoreStatement store = (FieldStoreStatement) stmt;
						String key = store.getName() + "." + store.getDesc();
						if(fieldsUsed.contains(key)) {
							canPropagate = false;
							break;
						}
					}
				} else if(stmt instanceof ArrayStoreStatement) {
					if(invoke.get() || array.get()) {
						canPropagate = false;
						break;
					}
				} else if(stmt instanceof MonitorStatement) {
					if(invoke.get()) {
						canPropagate = false;
						break;
					}
				}
			}
			
			if(!canPropagate) {
				return null;
			}
			
			// System.out.println("at " + use);
			
			AtomicBoolean canPropagate2 = new AtomicBoolean(canPropagate);
			if(invoke.get() || array.get() || !fieldsUsed.isEmpty()) {
				StatementVisitor vis2 = new StatementVisitor(stmt) {
					@Override
					public Statement visit(Statement stmt) {
						if(root == use && (stmt instanceof VarExpression && ((VarExpression) stmt).getLocal() == local)) {
							_break();
						} else {
							if(stmt instanceof InvocationExpression || (invoke.get() && (stmt instanceof FieldStoreStatement || stmt instanceof ArrayStoreStatement))) {
								canPropagate2.set(false);
								_break();
							}
						}
						return stmt;
					}
				};
				vis2.visit();
				canPropagate = canPropagate2.get();
			}
		}
		
		if(!canPropagate) {
			return null;
		}
		
		if(uses.getUses(localDef).size() > 1) {
			if(rhs.canChangeLogic() || rhs.canChangeFlow()) {
				return null;
			}
			return rhs;
			// System.out.println("SelfPropagate " + localDef);
			// System.out.println("uSED BY " + uses.getUses(localDef));
			// System.out.println(" Uses: " + uses.getUses(localDef));
			// definitions.remove(localDef);
			// liveness.remove(localDef);
			// graph.excavate(localDef);
			// root.delete(root.indexOf(localDef));
			// return rhs.copy();
		} else {
//			if(rhs instanceof VarExpression) {
//				if(((VarExpression) rhs).getLocal() == local) {
//					return null;
//				}
//			}
			// System.out.println();
			// System.out.println();
			// System.out.println("Enter pass:");
			// System.out.println(" Local: " + localDef.getVariable().getLocal());
			// System.out.println(" Def: " + localDef);
			// System.out.println(" Use: " + use);
			// System.out.println(" LUsed: " + localsUsed);
			// System.out.println(" FUsed: " + fieldsUsed);
			// System.out.println(" invoke: " + invoke.get() + ", array: " + array.get());
			// System.out.println(" On path; " + path);
			// System.out.println(" Propagate " + localDef +" into " + use);
			return rhs;
		}
	}
	
	private class Transformer extends StatementVisitor {

		private final Map<Local, Set<CopyVarStatement>> reachingDefs;
		private boolean change;

		public Transformer(Statement stmt) {
			super(stmt);
			reachingDefs = definitions.in(stmt);
		}
		
		private void transformSingleDef(CopyVarStatement localDef, VarExpression s, Local local) {
			Expression expr = null;
			if(localDef.getExpression() instanceof ConstantExpression) {
				expr = localDef.getExpression();
			} else {
				if(!local.isStack()) {
					return;
				}
				expr = transform(localDef, root);
			}
			
			if (expr != null) {
				changedStmts++;
				change = true;
				
				Statement r = getCurrent(getDepth());
				r.overwrite(expr, r.indexOf(s));
				
				boolean canRemoveDefinition = uses.getUses(localDef).size() <= 1;
				if (canRemoveDefinition) {
					CopyPropagator.this.root.delete(CopyPropagator.this.root.indexOf(localDef));

					definitions.remove(localDef);
					liveness.remove(localDef);
					if (!graph.excavate(localDef)) {
						// if we can't remove the def here,
						// then readd the thing
						definitions.update(localDef);
						liveness.update(localDef);
					}
					uses.remove(localDef);
				}
				definitions.update(root);
				liveness.update(root);

				if (canRemoveDefinition) {
					uses.remove(localDef);
				}
				definitions.processQueue();
				liveness.processQueue();
				uses.update(root);
			}
		}

		@Override
		public Statement visit(Statement s) {
			if(s instanceof VarExpression) {
				Local local = ((VarExpression) s).getLocal();
				
				Set<CopyVarStatement> defs = reachingDefs.get(local);
				
				if(defs.size() == 1)  {
					transformSingleDef(defs.iterator().next(), (VarExpression) s, local);
				} else {
					// example:
					//  L1: y = 0
					//      x = 0
					//      goto L3
					//  L2: x = y
					//  L3: print x
					// rhsLocals = [y]
					// varRhss = [{x=0}]
					
					// we need to check that at L1, x=z where y=z is also true.
					Set<Local> rhsLocals = new HashSet<>();
					Set<CopyVarStatement> varRhss = new HashSet<>();
					
					for(CopyVarStatement cvs : defs) {
						Expression expr = cvs.getExpression();
						if(expr instanceof VarExpression) {
							rhsLocals.add(((VarExpression) expr).getLocal());
						} else {
							varRhss.add(cvs);
						}
					}
					
					if(rhsLocals.size() != 1){
						return s;
					}
					
					Local rhsLocal = rhsLocals.iterator().next();
					
					boolean complex = false;
					
					// now we check that the value of each variable
					// rhsExpr rhs is the same as the rhs of the currentDef
					// at that point.
					for(CopyVarStatement cvs : varRhss) {
						Map<Local, Set<CopyVarStatement>> pointDefs = definitions.in(cvs);
						Set<CopyVarStatement> defVarDefs = pointDefs.get(rhsLocal);
						if(defVarDefs.size() != 1) {
							return s;
						}
						
						Expression rhs1 = cvs.getExpression();
						Expression rhs2 = defVarDefs.iterator().next().getExpression();
						if(!rhs1.equivalent(rhs2)) {
							return s;
						} else {
							if(!(rhs1 instanceof ConstantExpression)) {
								complex = true;
							}
						}
					}
					
					if(!complex) {
						changedStmts++;
						change = true;
						
						Statement r = getCurrent(getDepth());
						VarExpression expr = new VarExpression(rhsLocal, ((VarExpression) s).getType());
						r.overwrite(expr, r.indexOf(s));
						definitions.update(root);
						liveness.update(root);
						definitions.processQueue();
						liveness.processQueue();
						uses.update(root);
					} else {
						throw new UnsupportedOperationException("TODO");
					}
				}
			}
			return s;
		}
	}
	
	private Set<Statement> findPossibleExecutedStatements(Statement from, Statement to) {
		Set<Statement> visited = new HashSet<>();
		LinkedList<Statement> stack = new LinkedList<>();
		stack.add(from);
		
		while(!stack.isEmpty()) {
			Statement s = stack.pop();
			
			for(FlowEdge<Statement> e : graph.getEdges(s)) {
				if(e instanceof TryCatchEdge)
					continue;
				Statement succ = e.dst;
				if(succ != to && !visited.contains(succ)) {
					stack.add(succ);
					visited.add(succ);
				}
			}
		}
		
		return visited;
	}
	
	@Deprecated
	public List<Statement> findPath(Statement s, Statement target) {
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
			
			if(succs.size() <= p) {
				return null;
			}
			
			Statement i = succs.get(p);
			
			if(i == target) {
				stack.add(target);
				if(stack.get(0) == s) {
					stack.remove(0);
				}
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