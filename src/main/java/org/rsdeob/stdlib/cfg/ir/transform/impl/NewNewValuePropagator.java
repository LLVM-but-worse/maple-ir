package org.rsdeob.stdlib.cfg.ir.transform.impl;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
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

public class NewNewValuePropagator {
	
	private final RootStatement root;
	private final StatementGraph graph;
	private final Map<Statement, SyntheticStatement> synthetics;
	private DefinitionAnalyser definitions;
	private LivenessAnalyser liveness;
	private UsesAnalyser uses;
	private int changedStmts;
	
	public NewNewValuePropagator(RootStatement root, StatementGraph graph) {
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
				
//				Statement newStmt = stmt.copy();
				
				Transformer transformer = new Transformer(stmt);
				transformer.visit();
				
				if(transformer.change) {
					change.set(true);
					
//					definitions.update(stmt);
//					definitions.remove(stmt);
//					liveness.update(stmt);
//					liveness.remove(stmt);
					
					// graph.replace(stmt);
					// root.overwrite(newStmt, root.indexOf(stmt));
					
//					definitions.processQueue();
//					liveness.processQueue();
					continue;
				}
			}
			
			if(!change.get()) {
				break;
			} else {
				uses = new UsesAnalyser(root, graph, definitions);
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
//			System.out.println("no path ");
//			System.out.println("  " + real);
//			System.out.println(" to ");
//			System.out.println("  " + use);
//			System.out.println("    but found " + findPossibleExecutedStatements(real, use));
//			path = findPossibleExecutedStatements(real, use);
//			if(path == null)
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
//			System.out.println("SelfPropagate " + localDef);
//			System.out.println("uSED BY " + uses.getUses(localDef));
			return null;
//			System.out.println("  Uses: " + uses.getUses(localDef));
//			definitions.remove(localDef);
//			liveness.remove(localDef);
//			graph.excavate(localDef);
//			root.delete(root.indexOf(localDef));
//			return rhs.copy();
		} else {
			if(rhs instanceof VarExpression) {
				if(((VarExpression) rhs).getLocal() == local) {
					return null;
				}
			}
			
			
//			System.out.println();
//			System.out.println();
//			System.out.println("Enter pass:");
//			System.out.println("  Local: " + localDef.getVariable().getLocal());
//			System.out.println("  Def: " + localDef);
//			System.out.println("  Use: " + use);
//			System.out.println("  LUsed: " + localsUsed);
//			System.out.println("  FUsed: " + fieldsUsed);
//			System.out.println("  invoke: " + invoke.get() + ", array: " + array.get());
//			System.out.println("  On path; " + path);
			System.out.println("  Propagate " + localDef +"  into " + use);
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

		@Override
		public Statement visit(Statement s) {
			if(s instanceof VarExpression) {
				Local local = ((VarExpression) s).getLocal();
				Set<CopyVarStatement> defs = reachingDefs.get(local);
				
				if(defs.size() == 1)  {
					CopyVarStatement localDef = defs.iterator().next();
					Expression expr = transform(localDef, root);
					
					if(expr != null) {
						changedStmts++;
						change = true;
						
						BufferedWriter bw = null;
						try {
							System.out.println("    Pass" + changedStmts);
							bw = new BufferedWriter(new FileWriter(new File("C:/Users/Bibl/Desktop/tests/frame" + changedStmts + ".txt")));
							bw.write("Def: " + localDef.getId() + ". " + localDef);
							bw.newLine();
							bw.write("Use: " + root.getId() + ". " + root);
							bw.newLine();
							bw.write("Var: " + s.getId() + ". " + s);
							bw.newLine();
							Statement r = getCurrent(getDepth());
							bw.write("R: " + r.getId() +". " + r);
							bw.newLine();
							Expression exp = expr.copy();
							bw.write("Copy: " + exp.getId() + ". " + expr);
							bw.newLine();
							bw.write("RIndex(s): " + r.indexOf(s));
							bw.newLine();
							bw.write("Usecount: " +  uses.getUses(localDef).size());
							bw.newLine();
							bw.newLine();
							
							bw.write("PreRoot:");
							bw.newLine();
							System.out.println(root);
							bw.write(NewNewValuePropagator.this.root.toString());
							r.overwrite(expr, r.indexOf(s));
							boolean rem = uses.getUses(localDef).size() <= 1;
							if(rem) {
//								System.out.println("err  ");
//								for(Statement s1 : AnalysisHelper.findErrorNodes(r)) {
//									System.out.println("  " + s1.getId());
//								}
								System.out.println("r:  " + r);
//								System.out.println(expr + " overwrites " + s +"  in  " + r);
//								System.out.println("Removing def " + localDef);
								NewNewValuePropagator.this.root.delete(NewNewValuePropagator.this.root.indexOf(localDef));
								
								definitions.remove(localDef);
								liveness.remove(localDef);
								graph.excavate(localDef);
								uses.remove(localDef);
								System.out.println("remdef " + localDef.getId() +". " + localDef);
							}
							definitions.update(root);
							liveness.update(root);
							
							
							if(rem) {
								uses.remove(localDef);
							}
							definitions.processQueue();
							liveness.processQueue();
							uses.update(root);
							

							bw.newLine();
							bw.newLine();
							bw.newLine();
							bw.write("PostRoot:");
							bw.newLine();
							bw.write(NewNewValuePropagator.this.root.toString());
							bw.close();
							
						} catch(IOException e) {
							
						} catch(Exception e) {
							try {
								bw.flush();
								bw.close();
							} catch(IOException e1) {
								e1.printStackTrace();
							}
							throw e;
						}
						
						
//						return expr.copy();
					}
				} else {
					Set<Local> vars = new HashSet<>();
					for(CopyVarStatement def : defs) {
						Expression expr = def.getExpression();
						if(!(expr instanceof VarExpression)) {
							return s;
						}
						vars.add(((VarExpression) expr).getLocal());
					}
					
					if(vars.size() == 1) {
//						System.out.println();
//						System.out.println();
//						System.out.println("defs: " + defs);
//						System.out.println("use: " + root);
//						System.out.println("rets: " + vars);
//						System.out.println();
						Set<Expression> rets = new HashSet<>();
						for(CopyVarStatement def : defs) {
							rets.add(transform(def, root));
						}
//						System.out.println();
//						System.out.println("rets2: " + rets);
						
						if(rets.size() > 0) {
							Expression last = null;
							for(Expression v : rets) {
								if(v == null) {
									return s;
								}
								if(last != null && !last.equivalent(v)) {
									return s;
								}
								last = v;
							}
							changedStmts++;
							change = true;
//							return last.copy();
						}
					} else {
//						System.out.println();
//						System.out.println("ubervars: " + vars);
//						System.out.println("  " + root);
//						System.out.println("  " + defs);
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