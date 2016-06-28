package org.rsdeob.stdlib.ir.transform.impl;

import org.rsdeob.stdlib.ir.Local;
import org.rsdeob.stdlib.ir.RootStatement;
import org.rsdeob.stdlib.ir.StatementGraph;
import org.rsdeob.stdlib.ir.StatementVisitor;
import org.rsdeob.stdlib.ir.expr.*;
import org.rsdeob.stdlib.ir.stat.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

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
						definitions.removed(stmt);
						liveness.removed(stmt);
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
		Collection<Statement> path = graph.wanderAllTrails(real, use);
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
		} else {
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
		
		private void transformSingleDef(CopyVarStatement localDef, VarExpression use, Local local) {
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
				
				Statement r = getCurrent(getDepth());
				// Statement toReplace = r.read(r.indexOf(use));

				changedStmts++;
				change = true;
				r.overwrite(expr, r.indexOf(use));

				boolean canRemoveDefinition = uses.getUses(localDef).size() <= 1;
				if (canRemoveDefinition) {
					CopyPropagator.this.root.delete(CopyPropagator.this.root.indexOf(localDef));

					definitions.removed(localDef);
					liveness.removed(localDef);
					if (!graph.excavate(localDef)) {
						// if we can't remove the def here,
						// then readd the thing
						definitions.updated(localDef);
						liveness.updated(localDef);
					}
					uses.remove(localDef);
				}
				definitions.updated(root);
				liveness.updated(root);

				if (canRemoveDefinition) {
					uses.remove(localDef);
				}
				definitions.processQueue();
				liveness.processQueue();
				uses.update(root);
			
//				if (toReplace instanceof VarExpression && !((VarExpression) toReplace).getLocal().toString().equals(local.toString())) {}
			}
		}
		
		private void transformMultiDef(Set<CopyVarStatement> defs, VarExpression use) {
			// FIXME: check propagation constraints along
			// transfer paths.
			
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
				return;
			}
			
			Local rhsLocal = rhsLocals.iterator().next();
			
			boolean complex = false;
			
			// now we check that the value of each variable
			// rhsExpr rhs is the same as the rhs of the currentDef
			// at that point.
			for(CopyVarStatement cvs : varRhss) {
				Map<Local, Set<CopyVarStatement>> pointDefs = definitions.in(cvs);
				Set<CopyVarStatement> defVarDefs = pointDefs.get(rhsLocal);
				if(defVarDefs == null || defVarDefs.size() != 1) {
					return;
				}
				
				Expression rhs1 = cvs.getExpression();
				Expression rhs2 = defVarDefs.iterator().next().getExpression();
				if(!rhs1.equivalent(rhs2)) {
					return;
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
				VarExpression expr = new VarExpression(rhsLocal, ((VarExpression) use).getType());
				r.overwrite(expr, r.indexOf(use));
				definitions.updated(root);
				liveness.updated(root);
				definitions.processQueue();
				liveness.processQueue();
				uses.update(root);
			} else {
				throw new UnsupportedOperationException("TODO");
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
					transformMultiDef(defs, (VarExpression) s);
				}
			}
			return s;
		}
	}
}