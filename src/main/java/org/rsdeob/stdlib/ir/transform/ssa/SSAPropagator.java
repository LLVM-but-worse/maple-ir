package org.rsdeob.stdlib.ir.transform.ssa;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.rsdeob.stdlib.collections.NullPermeableHashMap;
import org.rsdeob.stdlib.collections.ValueCreator;
import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.StatementVisitor;
import org.rsdeob.stdlib.ir.expr.*;
import org.rsdeob.stdlib.ir.locals.Local;
import org.rsdeob.stdlib.ir.locals.VersionedLocal;
import org.rsdeob.stdlib.ir.stat.ArrayStoreStatement;
import org.rsdeob.stdlib.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.ir.stat.FieldStoreStatement;
import org.rsdeob.stdlib.ir.stat.MonitorStatement;
import org.rsdeob.stdlib.ir.stat.PopStatement;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.transform.Transformer;

public class SSAPropagator extends Transformer {

	private final Map<VersionedLocal, CopyVarStatement> defs;
	private final NullPermeableHashMap<VersionedLocal, AtomicInteger> useCount;
	private final StatementGraph graph;
	
	public SSAPropagator(CodeBody code, StatementGraph graph) {
		super(code, null);
		this.graph = graph;
		defs = new HashMap<>();
		useCount = new NullPermeableHashMap<>(new ValueCreator<AtomicInteger>() {
			@Override
			public AtomicInteger create() {
				return new AtomicInteger();
			}
		});
		
		for(Statement s : code) {
			boolean synth = false;
			
			if(s instanceof CopyVarStatement) {
				CopyVarStatement d = (CopyVarStatement) s;
				VersionedLocal local = (VersionedLocal) d.getVariable().getLocal();
				defs.put(local, d);
				// sometimes locals can be dead even without any transforms.
				// since they have no uses, they are never found by the below
				// visitor, so we touch the local in map here to mark it.
				useCount.getNonNull(local); 
				synth = d.isSynthetic();
			}
			
			if(!synth) {
				new StatementVisitor(s) {
					@Override
					public Statement visit(Statement stmt) {
						if(stmt instanceof VarExpression) {
							VersionedLocal l = (VersionedLocal) ((VarExpression) stmt).getLocal();
							useCount.getNonNull(l).incrementAndGet();
						} else if(stmt instanceof PhiExpression) {
							PhiExpression phi = (PhiExpression) stmt;
							for(VersionedLocal l : phi.getLocals()) {
								useCount.getNonNull(l).incrementAndGet();
							}
						}
						return stmt;
					}
				}.visit();
			}
		}
	}

	@Override
	public int run() {
		AtomicInteger changes = new AtomicInteger();
		for(Statement stmt : new HashSet<>(code)) {
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
						Local l = ((VarExpression) s).getLocal();
						if(l instanceof VersionedLocal) {
							CopyVarStatement def = defs.get(l);
							if(def == null) {
								System.err.println(code);
								System.err.println("using " + l);
							}
							Expression expr = def.getExpression();
							if(expr instanceof PhiExpression) {
								System.out.println("skipping phi at " + def);
								return s;
							}
							
							if(expr instanceof ConstantExpression) {
								useCount.get(l).decrementAndGet();
								return expr;
							} else if(expr instanceof VarExpression) {
								VarExpression var = (VarExpression) expr;
								useCount.get(var.getLocal()).incrementAndGet();
								useCount.get(l).decrementAndGet();
								return var;
							} else if(!(expr instanceof CaughtExceptionExpression)) {
								Expression e = transform(stmt, s, def);
								if(e != null) {
									if(e instanceof VarExpression) {
										useCount.get(((VarExpression) e).getLocal()).incrementAndGet();
										useCount.get(l).decrementAndGet();
										return e;
									} else if(e instanceof InvocationExpression || e instanceof InitialisedObjectExpression || e instanceof UninitialisedObjectExpression) {
										if(useCount.get(l).get() == 1) {
											useCount.get(l).decrementAndGet();
											removeDef(def, false);
											useCount.remove(l);
											return e;
										}
									} else {
										useCount.get(l).decrementAndGet();
										return e;
									}
								} else {
									System.out.println("Cannot propagate " + def + " to " + stmt);
								}
							}
						} else {
							throw new UnsupportedOperationException("Only SSA body allowed.");
						}
					}
					return s;
				}
			}.visit();
		}

		Iterator<Entry<VersionedLocal, AtomicInteger>> it = useCount.entrySet().iterator();
		while(it.hasNext()) {
			Entry<VersionedLocal, AtomicInteger> e = it.next();
			if(e.getValue().get() == 0)  {
				CopyVarStatement def = defs.get(e.getKey());
				if(removeDef(def, true)) {
					it.remove();
					changes.incrementAndGet();
				}
			}
		}
		
		return changes.get();
	}
	
	private boolean removeDef(CopyVarStatement def, boolean save) {
		if(!def.isSynthetic()) {
			defs.remove(def);
			
			Expression rhs = def.getExpression();
			if(save && rhs instanceof InvocationExpression || rhs instanceof InitialisedObjectExpression) {
				PopStatement pop = new PopStatement(rhs);
				code.set(code.indexOf(def), pop);
			} else {
				code.remove(def);
			}
			
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
						System.out.println("root==use: " + (root == use));
						System.out.println("   root:  " + root);
						System.out.println("   use:   " + use);
						if(root == use && (s instanceof VarExpression && ((VarExpression) s).getLocal() == local)) {
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