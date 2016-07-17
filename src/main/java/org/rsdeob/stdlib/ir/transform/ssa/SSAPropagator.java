package org.rsdeob.stdlib.ir.transform.ssa;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.edge.DummyEdge;
import org.rsdeob.stdlib.collections.NullPermeableHashMap;
import org.rsdeob.stdlib.collections.SetCreator;
import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.StatementVisitor;
import org.rsdeob.stdlib.ir.expr.ArrayLoadExpression;
import org.rsdeob.stdlib.ir.expr.CaughtExceptionExpression;
import org.rsdeob.stdlib.ir.expr.ConstantExpression;
import org.rsdeob.stdlib.ir.expr.Expression;
import org.rsdeob.stdlib.ir.expr.FieldLoadExpression;
import org.rsdeob.stdlib.ir.expr.InitialisedObjectExpression;
import org.rsdeob.stdlib.ir.expr.InvocationExpression;
import org.rsdeob.stdlib.ir.expr.PhiExpression;
import org.rsdeob.stdlib.ir.expr.UninitialisedObjectExpression;
import org.rsdeob.stdlib.ir.expr.VarExpression;
import org.rsdeob.stdlib.ir.header.HeaderStatement;
import org.rsdeob.stdlib.ir.locals.Local;
import org.rsdeob.stdlib.ir.locals.VersionedLocal;
import org.rsdeob.stdlib.ir.stat.ArrayStoreStatement;
import org.rsdeob.stdlib.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.ir.stat.DummyExitStatement;
import org.rsdeob.stdlib.ir.stat.FieldStoreStatement;
import org.rsdeob.stdlib.ir.stat.MonitorStatement;
import org.rsdeob.stdlib.ir.stat.PopStatement;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.stat.SyntheticCopyStatement;
import org.rsdeob.stdlib.ir.transform.SSATransformer;

public class SSAPropagator extends SSATransformer {

	private final StatementGraph graph;
	private final Collection<? extends HeaderStatement> headers;
	private final DummyExitStatement exit;
	
	public SSAPropagator(CodeBody code, SSALocalAccess localAccess, StatementGraph graph, Collection<? extends HeaderStatement> headers) {
		super(code, localAccess);
		this.graph = graph;
		this.headers = headers;
		exit = new DummyExitStatement();
	}

	@Override
	public int run() {
		graph.addVertex(exit);
		for(Statement b : graph.vertices()) {
			// connect dummy exit
			if(graph.getEdges(b).size() == 0) {
				graph.addEdge(b, new DummyEdge<>(b, exit));
			}
		}
		
		AtomicInteger changes = new AtomicInteger();
		for(Statement stmt : new HashSet<>(code)) {
			
			if(stmt instanceof PopStatement) {
				PopStatement pop = (PopStatement) stmt;
				Expression expr = pop.getExpression();
				if(expr instanceof VarExpression) {
					VarExpression var = (VarExpression) expr;
					Local l = var.getLocal();
					localAccess.useCount.get(l).decrementAndGet();
					code.remove(pop);
					graph.excavate(pop);
					continue;
				} else if(expr instanceof ConstantExpression) {
					code.remove(pop);
					graph.excavate(pop);
					continue;
				}
			}

			new StatementVisitor(stmt) {

				@Override
				protected void visited(Statement stmt, Statement node, int addr, Statement vis) {
					if(vis != node) {
						stmt.overwrite(vis, addr);
						changes.incrementAndGet();
					}
				}

				@Override
				public Statement visit(Statement s) {
					if(s instanceof VarExpression) {
						Expression e = transform(stmt, (VarExpression) s);
						if(e != null) {
							return e;
						}
					} else if(s instanceof PhiExpression){
						PhiExpression phi = (PhiExpression) s;
						for(HeaderStatement header : phi.headers()) {
							Expression e = phi.getLocal(header);
							if(e instanceof VarExpression) {
								e = transform(stmt, (VarExpression) e);
								if(e != null) {
									phi.setLocal(header, e);
								}
							}
						}
					}
					return s;
				}
			}.visit();
		}
		
		// equivalent phis
		for(HeaderStatement header : headers) {
			List<CopyVarStatement> phis = new ArrayList<>();
			for(int i=code.indexOf(header) + 1; i < code.size(); i++) {
				Statement stmt = code.get(i);
				if(stmt instanceof CopyVarStatement) {
					CopyVarStatement cv = (CopyVarStatement) stmt;
					if(cv.getExpression() instanceof PhiExpression) {
						phis.add(cv);
						continue;
					}
				}
				break;
			}
			
			if(phis.size() > 1) {
				NullPermeableHashMap<CopyVarStatement, Set<CopyVarStatement>> equiv = new NullPermeableHashMap<>(new SetCreator<>());
				for(CopyVarStatement cvs : phis) {
					if(equiv.values().contains(cvs)) {
						continue;
					}
					PhiExpression phi = (PhiExpression) cvs.getExpression();
					for(CopyVarStatement cvs2 : phis) {
						if(cvs != cvs2) {
							if(equiv.keySet().contains(cvs2)) {
								continue;
							}
							PhiExpression phi2 = (PhiExpression) cvs2.getExpression();
							if(phi.equivalent(phi2)) {
								equiv.getNonNull(cvs).add(cvs2);
							}
						}
					}
				}
				
				for(Entry<CopyVarStatement, Set<CopyVarStatement>> e : equiv.entrySet()) {
					// key should be earliest
					// remove vals from code and replace use of val vars with key var
					CopyVarStatement proper = e.getKey();
					VersionedLocal properLocal = (VersionedLocal) proper.getVariable().getLocal();
					Set<VersionedLocal> toReplace = new HashSet<>();
					for(CopyVarStatement val : e.getValue()) {
						VersionedLocal local = (VersionedLocal) val.getVariable().getLocal();
						toReplace.add(local);
						graph.excavate(val);
						code.remove(val);
						localAccess.defs.remove(local);
						localAccess.useCount.remove(local);
					}
					// replace uses
					for(Statement reachable : graph.wanderAllTrails(proper, exit)) {
						new StatementVisitor(reachable) {
							@Override
							public Statement visit(Statement stmt) {
								if(stmt instanceof VarExpression) {
									VarExpression var = (VarExpression) stmt;
									if(toReplace.contains(var.getLocal())) {
										localAccess.useCount.get(properLocal).incrementAndGet();
										return new VarExpression(properLocal, var.getType());
									}
								}
								return stmt;
							}
						}.visit();
					}
				}
			}
		}

		Iterator<Entry<VersionedLocal, AtomicInteger>> it = localAccess.useCount.entrySet().iterator();
		while(it.hasNext()) {
			Entry<VersionedLocal, AtomicInteger> e = it.next();
			if(e.getValue().get() == 0)  {
				CopyVarStatement def = localAccess.defs.get(e.getKey());
				if(removeDef(def, true)) {
					it.remove();
					changes.incrementAndGet();
				}
			}
		}
		
		graph.removeVertex(exit);

		return changes.get();
	}

	private Expression transform(Statement stmt, VarExpression s) {
		Local l = s.getLocal();
		if (!(l instanceof VersionedLocal)) {
			throw new UnsupportedOperationException("Only SSA body allowed.");
		}

		CopyVarStatement def = localAccess.defs.get(l);
		if (def == null) {
			System.err.println(code);
			System.err.println("using " + l);
		}

		Expression expr = def.getExpression();
		if (expr instanceof PhiExpression) {
			return s;
		}

		if (expr instanceof ConstantExpression) {
			localAccess.useCount.get(l).decrementAndGet();
			System.out.println("Propagating " + expr + " into " + stmt);
			return expr;
		} else if (expr instanceof VarExpression) {
			VarExpression var = (VarExpression) expr;
			localAccess.useCount.get(var.getLocal()).incrementAndGet();
			if (localAccess.useCount.get(l).decrementAndGet() == 0) {
				removeDef(def, false);
				localAccess.useCount.remove(l);
			}
			System.out.println("Propagating " + expr + " into " + stmt);
			return var;
		} else if (!(expr instanceof CaughtExceptionExpression)) {
			Expression e = transform(stmt, s, def);
			if (e != null) {
				if (e instanceof VarExpression) {
					localAccess.useCount.get(((VarExpression) e).getLocal()).incrementAndGet();
					if (localAccess.useCount.get(l).decrementAndGet() == 0) {
						removeDef(def, false);
						localAccess.useCount.remove(l);
					}
					System.out.println("Propagating " + e + " into " + stmt);
					return e;
				} else if (e instanceof InvocationExpression || e instanceof InitialisedObjectExpression || e instanceof UninitialisedObjectExpression) {
					if (localAccess.useCount.get(l).get() == 1) {
						localAccess.useCount.get(l).decrementAndGet();
						removeDef(def, false);
						localAccess.useCount.remove(l);
						System.out.println("Propagating " + e + " into " + stmt);
						return e;
					}
				} else {
					localAccess.useCount.get(l).decrementAndGet();
					System.out.println("Propagating " + e + " into " + stmt);
					return e;
				}
			} else {
				System.out.println("Cannot propagate " + def + " to " + stmt);
			}
		}
		return null;
	}

	private boolean removeDef(CopyVarStatement def, boolean save) {
		if(!(def instanceof SyntheticCopyStatement)) {
			localAccess.defs.remove(def);
			
			Expression rhs = def.getExpression();
			if(save && rhs instanceof InvocationExpression || rhs instanceof InitialisedObjectExpression) {
				PopStatement pop = new PopStatement(rhs);
				code.set(code.indexOf(def), pop);
			} else {
				code.remove(def);
			}
			
			graph.excavate(def);
			
			System.out.println("Removed dead def: " + def);
			return true;
		}
		return false;
	}
	
	private Expression transform(Statement use, Statement tail, CopyVarStatement def) {
		Local local = def.getVariable().getLocal();
		Expression rhs = def.getExpression();

		Set<String> fieldsUsed = new HashSet<>();
		AtomicBoolean invoke = new AtomicBoolean();
		AtomicBoolean array = new AtomicBoolean();
		
		{
			if(rhs instanceof FieldLoadExpression) {
				fieldsUsed.add(((FieldLoadExpression) rhs).getName() + "." + ((FieldLoadExpression) rhs).getDesc());
			} else if(rhs instanceof InvocationExpression || rhs instanceof InitialisedObjectExpression) {
				invoke.set(true);
			} else if(rhs instanceof ArrayLoadExpression) {
				array.set(true);
			} else if(rhs instanceof ConstantExpression) {
				return rhs;
			}
		}
		
		new StatementVisitor(rhs) {
			@Override
			public Statement visit(Statement stmt) {
				if(stmt instanceof FieldLoadExpression) {
					fieldsUsed.add(((FieldLoadExpression) stmt).getName() + "." + ((FieldLoadExpression) stmt).getDesc());
				} else if(stmt instanceof InvocationExpression || stmt instanceof InitialisedObjectExpression) {
					invoke.set(true);
				} else if(stmt instanceof ArrayLoadExpression) {
					array.set(true);
				}
				return stmt;
			}
		}.visit();
		
		Set<Statement> path = graph.wanderAllTrails(def, use);
		path.remove(def);
		path.add(use);
		
		boolean canPropagate = true;
		
		for(Statement stmt : path) {
			if(stmt != use) {
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
				} else if(stmt instanceof InitialisedObjectExpression || stmt instanceof InvocationExpression) {
					if(invoke.get() || fieldsUsed.size() > 0 || array.get()) {
						canPropagate = false;
						break;
					}
				}
			}
			
			if(!canPropagate) {
				return null;
			}
			
			AtomicBoolean canPropagate2 = new AtomicBoolean(canPropagate);
			if(invoke.get() || array.get() || !fieldsUsed.isEmpty()) {
				new StatementVisitor(stmt) {
					@Override
					public Statement visit(Statement s) {
						// if(use.toString().equals("svar0_5 = svar0_3.append(a.a.a.d.H(\"O\")).append(lvar2_0);")) {
							// System.out.println(code);
							// System.out.println("root==use: " + (root == use));
							//  System.out.println("   fullcond: " + (root == use && (s instanceof VarExpression && ((VarExpression) s).getLocal() == local)));
							// 	 System.out.println("   s: " + s);
							//  System.out.println("   l: " + local);
							//  System.out.println("   root:  " + root);
							//  System.out.println("   use:   " + use);
							//  System.out.println("   inst: " + this.hashCode());
							// System.exit(1);
						// }
						
						if(root == use && (s instanceof VarExpression && ((VarExpression) s).getLocal() == local)) {
							// System.out.println("Breaking " + this.hashCode());
							_break();
						} else {
							if((s instanceof InvocationExpression || s instanceof InitialisedObjectExpression) || (invoke.get() && (s instanceof FieldStoreStatement || s instanceof ArrayStoreStatement))) {
								canPropagate2.set(false);
								_break();
							}
						}
						return s;
					}
				}.visit();
				canPropagate = canPropagate2.get();
				
				if(!canPropagate) {
					return null;
				}
			}
		}
		
		if(canPropagate) {
			return rhs;
		}
		
		return null;
	}
}