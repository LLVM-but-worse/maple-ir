package org.rsdeob.stdlib.ir.transform.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.rsdeob.AnalyticsTest;
import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.StatementVisitor;
import org.rsdeob.stdlib.ir.expr.ArrayLoadExpression;
import org.rsdeob.stdlib.ir.expr.ConstantExpression;
import org.rsdeob.stdlib.ir.expr.Expression;
import org.rsdeob.stdlib.ir.expr.FieldLoadExpression;
import org.rsdeob.stdlib.ir.expr.InitialisedObjectExpression;
import org.rsdeob.stdlib.ir.expr.InvocationExpression;
import org.rsdeob.stdlib.ir.expr.VarExpression;
import org.rsdeob.stdlib.ir.locals.Local;
import org.rsdeob.stdlib.ir.stat.ArrayStoreStatement;
import org.rsdeob.stdlib.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.ir.stat.FieldStoreStatement;
import org.rsdeob.stdlib.ir.stat.MonitorStatement;
import org.rsdeob.stdlib.ir.stat.PopStatement;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.stat.SyntheticStatement;

public class CopyPropagator extends Transformer {

	private final Map<Statement, SyntheticStatement> synthetics;
	private int changedStmts;
	
	public CopyPropagator(CodeBody root, CodeAnalytics analytics) {
		super(root, analytics);
		
		synthetics = new HashMap<>();
		for(Statement stmt : root) {
			if(stmt instanceof SyntheticStatement) {
				synthetics.put(((SyntheticStatement) stmt).getStatement(), (SyntheticStatement) stmt);
			}
		}
	}
	
	@Override
	public int run() {
		changedStmts = 0;
		processImpl();
		return changedStmts;
	}
	
	private void processImpl() {
		while(true) {
			boolean change = false;
			
			List<Statement> stmts = new ArrayList<>();
			for(Statement stmt : code) {
				stmts.add(stmt);
			}
			for(Statement stmt : stmts) {
				if(stmt instanceof SyntheticStatement)
					continue;
				
				if(stmt instanceof PopStatement) {
					Expression expr = ((PopStatement) stmt).getExpression();
					if(expr instanceof ConstantExpression || expr instanceof VarExpression) {
						code.remove(stmt);
						code.commit();
						AnalyticsTest.verify_callback(code, analytics, stmt);
						
						change = true;
						changedStmts++;
						continue;
					}
				}
				
				Transformer transformer = new Transformer(stmt);
				transformer.visit();
				
				if(transformer.change) {
					AnalyticsTest.verify_callback(code, analytics, stmt);
					change = true;
					continue;
				}

			}
			
			if(!change) {
				break;
			}
		}
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
			} else if(rhs instanceof InvocationExpression || rhs instanceof InitialisedObjectExpression) {
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
				} else if(stmt instanceof InvocationExpression || stmt instanceof InitialisedObjectExpression) {
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
		Collection<Statement> path = analytics.sgraph.wanderAllTrails(real, use);
		path.remove(real);
//		if(path.isEmpty()) {
//			return null;
//		}
		
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
						if(root == use && (s instanceof VarExpression && ((VarExpression) s).getLocal() == local)) {
							_break();
						} else {
							if((s instanceof InvocationExpression || s instanceof InitialisedObjectExpression) || (invoke.get() && (s instanceof FieldStoreStatement || s instanceof ArrayStoreStatement))) {
								canPropagate2.set(false);
								System.out.println("Failing on: " + s.getId() + ". " + s);
								System.out.println("  Root: " + root.getId() + ". " + root);
								System.out.println("  Use: " + use.getId() + ". " + use);
								System.out.println("  RHS: " + rhs.getId() + ". " + rhs);
								System.out.println("  Def: " + localDef.getId() + ". " +  localDef);
								_break();
							}
						}
						return s;
					}
				}.visit();
				canPropagate = canPropagate2.get();
				
				if(!canPropagate) {
					System.out.println("HERE NIGGER " + localDef.getId() + ". " + localDef);
					return null;
				}
			}
		}
		
		if(!canPropagate) {
			return null;
		}
		
		if(analytics.uses.getUses(localDef).size() > 1) {
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
			reachingDefs = analytics.definitions.in(stmt);
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

				changedStmts++;
				change = true;
				
				r.overwrite(expr, r.indexOf(use));
				CopyPropagator.this.code.forceUpdate(root);
				boolean canRemoveDefinition = analytics.uses.getUses(localDef).size() <= 1;
				if (canRemoveDefinition) {
//					CopyPropagator.this.code.commit();
					CopyPropagator.this.code.remove(localDef);
				}
				CopyPropagator.this.code.commit();
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
				Map<Local, Set<CopyVarStatement>> pointDefs = analytics.definitions.in(cvs);
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
				VarExpression expr = new VarExpression(rhsLocal, use.getType());
				r.overwrite(expr, r.indexOf(use));
				CopyPropagator.this.code.forceUpdate(root);
				CopyPropagator.this.code.commit();
			} else {
				throw new UnsupportedOperationException("TODO");
			}
		}

		@Override
		public Statement visit(Statement s) {
			if(s instanceof VarExpression) {
				Local local = ((VarExpression) s).getLocal();
				
				Set<CopyVarStatement> defs = reachingDefs.get(local);
				if(defs == null) {
					System.err.println(code);
					System.err.println("no defs for " + root);
				}
				
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