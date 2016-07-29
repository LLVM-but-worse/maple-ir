package org.mapleir.stdlib.ir.transform.ssa;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.mapleir.stdlib.cfg.edge.DummyEdge;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.ir.CodeBody;
import org.mapleir.stdlib.ir.StatementGraph;
import org.mapleir.stdlib.ir.StatementVisitor;
import org.mapleir.stdlib.ir.expr.*;
import org.mapleir.stdlib.ir.header.HeaderStatement;
import org.mapleir.stdlib.ir.locals.Local;
import org.mapleir.stdlib.ir.locals.VersionedLocal;
import org.mapleir.stdlib.ir.stat.ArrayStoreStatement;
import org.mapleir.stdlib.ir.stat.CopyVarStatement;
import org.mapleir.stdlib.ir.stat.DummyExitStatement;
import org.mapleir.stdlib.ir.stat.FieldStoreStatement;
import org.mapleir.stdlib.ir.stat.MonitorStatement;
import org.mapleir.stdlib.ir.stat.PopStatement;
import org.mapleir.stdlib.ir.stat.Statement;
import org.mapleir.stdlib.ir.transform.SSATransformer;

public class SSAPropagator extends SSATransformer {
	
	private static final Set<Class<? extends Statement>> UNCOPYABLE = new HashSet<>();
	
	static {
		UNCOPYABLE.add(InvocationExpression.class);
		UNCOPYABLE.add(UninitialisedObjectExpression.class);
		UNCOPYABLE.add(InitialisedObjectExpression.class);
	}
	
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
		
		FeedbackStatementVisitor visitor = new FeedbackStatementVisitor(null);
		AtomicInteger changes = new AtomicInteger();
		for(Statement stmt : new HashSet<>(code)) {
			if(!code.contains(stmt))
				continue;
			if(attempt(stmt, visitor)) changes.incrementAndGet();
			if(visitor.cleanDead()) changes.incrementAndGet();
			if(visitor.cleanEquivalentPhis()) changes.incrementAndGet();
		}
		
		graph.removeVertex(exit);
		return changes.get();
	}
	
	private boolean attempt(Statement stmt, FeedbackStatementVisitor visitor) {
		if(stmt instanceof PopStatement) {
			boolean at = attemptPop((PopStatement)stmt);
			if(at) {
				return true;
			}
		}

		visitor.reset(stmt);
		visitor.visit();
		return visitor.changed();
	}
	
	private boolean attemptPop(PopStatement pop) {
		Expression expr = pop.getExpression();
		if(expr instanceof VarExpression) {
			VarExpression var = (VarExpression) expr;
			localAccess.useCount.get(var.getLocal()).decrementAndGet();
			code.remove(pop);
			graph.excavate(pop);
			return true;
		} else if(expr instanceof ConstantExpression) {
			code.remove(pop);
			graph.excavate(pop);
			return true;
		}
		return false;
	}
	
	private class FeedbackStatementVisitor extends StatementVisitor {
		
		private boolean change = false;
		
		public FeedbackStatementVisitor(Statement root) {
			super(root);
		}
		
		private boolean cleanEquivalentPhis() {
			boolean change = false;
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
						CopyVarStatement keepPhi = e.getKey();
						VersionedLocal phiLocal = (VersionedLocal) keepPhi.getVariable().getLocal();
						Set<VersionedLocal> toReplace = new HashSet<>();
						for(CopyVarStatement def : e.getValue()) {
							VersionedLocal local = (VersionedLocal) def.getVariable().getLocal();
							toReplace.add(local);
							killed(def);
							code.remove(def);
							graph.excavate(def);
						}
						
						// replace uses
						for(Statement reachable : graph.wanderAllTrails(keepPhi, exit)) {
							for(Statement s : Statement.enumerate(reachable)) {
								if(s instanceof VarExpression) {
									VarExpression var = (VarExpression) s;
									VersionedLocal l = (VersionedLocal) var.getLocal();
									if(toReplace.contains(l)) {
										reuseLocal(phiLocal);
										unuseLocal(l);
										var.setLocal(phiLocal);
									}
								}
							}
						}
						
						for(CopyVarStatement def : e.getValue()) {
							Local local = def.getVariable().getLocal();
							localAccess.useCount.remove(local);
							localAccess.defs.remove(local);
						}
						change = true;
					}
				}
			}
			return change;
		}
		
		/**
		 * Remove all definitions of versions of locals that have no uses
		 * @return whether any locals were removed
		 */
		private boolean cleanDead() {
			boolean changed = false;
			Iterator<Entry<VersionedLocal, AtomicInteger>> it = localAccess.useCount.entrySet().iterator();
			while(it.hasNext()) {
				Entry<VersionedLocal, AtomicInteger> e = it.next();
				if(e.getValue().get() == 0)  {
					CopyVarStatement def = localAccess.defs.get(e.getKey());
					if(!def.isSynthetic()) {
						if(!fineBladeDefinition(def, it)) {
							killed(def);
							changed = true;
						}
					}
				}
			}
			return changed;
		}
		
		/**
		 * Called when a statement is removed.
		 * Updates the use counter of the locals by removing the uses caused by the var references in this statement.
		 * @param stmt Statement that was removed
		 */
		private void killed(Statement stmt) {
			for(Statement s : Statement.enumerate(stmt)) {
				if(s instanceof VarExpression) {
					unuseLocal(((VarExpression) s).getLocal());
				}
			}
		}
		
		/**
		 * Called when a statement is added into the code (to replace a var reference).
		 * Updates the use counters of the locals by adding the uses caused by the var references in this statement.
		 * @param stmt Statement that was added
		 */
		private void copied(Statement stmt) {
			for(Statement s : Statement.enumerate(stmt)) {
				if(s instanceof VarExpression) {
					reuseLocal(((VarExpression) s).getLocal());
				}
			}
		}
		
		/**
		 * Removes a def and cleans up after it, taking caution for uncopyable statements (i.e. invokes).
		 * Updates the codebody, graph, and local tracker.
		 * @param def Definition to remove.
		 * @return Whether the given def was uncopyable.
		 */
		private boolean fineBladeDefinition(CopyVarStatement def, Iterator<?> it) {
			it.remove();
			Expression rhs = def.getExpression();
			if(isUncopyable(rhs)) {
				PopStatement pop = new PopStatement(rhs);
				code.set(code.indexOf(def), pop);
				graph.replace(def, pop);
				return true;
			} else {
				// easy remove
				code.remove(def);
				graph.excavate(def);
				Local local = def.getVariable().getLocal();
				localAccess.useCount.remove(local);
				return false;
			}
		}
		
		/**
		 * Remoevs a def and updates the codebody, graph, and local tracker. Does not consider copyability of statement.
		 * @param def Definition to remove.
		 */
		private void scalpelDefinition(CopyVarStatement def) {
			code.remove(def);
			graph.excavate(def);
			Local local = def.getVariable().getLocal();
			localAccess.useCount.remove(local);
			localAccess.defs.remove(local);
		}
		

		/**
		 * Safely gets the number of uses for a given local.
		 * @param l Local to get uses count for
		 * @return Number of uses for the given local.
		 */
		private int uses(Local l) {
			if(localAccess.useCount.containsKey(l)) {
				return localAccess.useCount.get(l).get();
			} else {
				throw new IllegalStateException("Local " + l + " not in useCount map. Def: " + localAccess.defs.get(l));
			}
		}

		/**
		 * Safely increments or decrements the use count for the given local.
		 * @param l Local to update the use count for.
		 * @param re If true, the use count is incremented; otherwise, it is decremented.
		 */
		private void _xuselocal(Local l, boolean re) {
			if(localAccess.useCount.containsKey(l)) {
				if(re) {
					localAccess.useCount.get(l).incrementAndGet();
				} else {
					localAccess.useCount.get(l).decrementAndGet();
				}
			} else {
				throw new IllegalStateException("Local " + l + " not in useCount map. Def: " + localAccess.defs.get(l));
			}
		}
		
		/**
		 * Decrements use counter of local
		 * @param l Local to update use counter for.
		 */
		private void unuseLocal(Local l) {
			_xuselocal(l, false);
		}
		
		/**
		 * increment use counter of local
		 * @param l Local to update use counter for.
 		 */
		private void reuseLocal(Local l) {
			_xuselocal(l, true);
		}
		
		// Inline a constant into the given use of the variable
		private Statement handleConstant(CopyVarStatement def, VarExpression use, ConstantExpression rhs) {
			// x = 7;
			// use(x)
			//         goes to
			// x = 7
			// use(7)
			
			// localCount -= 1;
			unuseLocal(use.getLocal());
			return rhs.copy();
		}

		// Inline a variable in the place of a variable which is defined as said variable
		private Statement handleVar(CopyVarStatement def, VarExpression use, VarExpression rhs) {
			Local x = use.getLocal();
			Local y = rhs.getLocal();
			if(x == y) {
				return null;
			}
			// x = y
			// use(x)
			//         goes to
			// x = y
			// use(y)
			
			// rhsCount += 1;
			// useCount -= 1;
			reuseLocal(y);
			unuseLocal(x);
			return rhs.copy();
		}

		// Inline a complex expression into the use of the variable which contains its value
		private Statement handleComplex(CopyVarStatement def, VarExpression use) {
			if(!canTransferToUse(root, use, def)) {
				return null;
			}
			
			// this can be propagated
			Expression propagatee = def.getExpression();
			if(isUncopyable(propagatee)) {
				// say we have
				// 
				// void test() {
				//    x = func();
				//    use(x);
				//    use(x);
				// }
				//
				// int func() {
				//    print("blowing up reactor core " + (++core));
				//    return core;
				// }
				// 
				// if we lazily propagated the rhs (func()) into both uses
				// it would blow up two reactor cores instead of the one
				// that it currently is set to destroy. this is why uncop-
				// yable statements (in reality these are expressions) ne-
				// ed to have only  one definition for them to be propaga-
				// table. at the moment the only possible expressions that
				// have these side effects are invoke type ones.
				if(uses(use.getLocal()) == 1) {
					// since there is only 1 use of this expression, we
					// will copy the propagatee/rhs to the use and then
					// remove the definition. this means that the only
					// change to uses is the variable that was being
					// propagated. i.e.
					
					// svar0_1 = lvar0_0.invoke(lvar1_0, lvar3_0.m)
					// use(svar0_1)
					//  will become
					// use(lvar0_0.invoke(lvar1_0, lvar3_0.m))
					
					// here the only thing we need to change is
					// the useCount of svar0_1 to 0. (1 - 1)
					unuseLocal(use.getLocal());
					scalpelDefinition(def);
					return propagatee;
				}
			} else {
				// these statements here can be copied as many times
				// as required without causing multiple catastrophic
				// reactor meltdowns.
				if(propagatee instanceof ArrayLoadExpression) {
					// TODO: CSE instead of this cheap assumption.
					if(uses(use.getLocal()) == 1) {
						unuseLocal(use.getLocal());
						scalpelDefinition(def);
						return propagatee;
					}
				} else {
					// x = ((y * 2) + (9 / lvar0_0.g))
					// use(x)
					//       goes to
					// x = ((y * 2) + (9 / lvar0_0.g))
					// use(((y * 2) + (9 / lvar0_0.g)))
					Local local = use.getLocal();
					unuseLocal(local);
					copied(propagatee);
					if(uses(local) == 0) {
						// if we just killed the local
						killed(def);
						scalpelDefinition(def);
					}
					return propagatee;
				}
			}
			return null;
		}
		
		// Attempt to propagate the given def into the given use.
		private Statement findSubstitution(Statement root, CopyVarStatement def, VarExpression use) {
			Local local = use.getLocal();
			if(!local.isStack()) {
				if(root instanceof CopyVarStatement) {
					CopyVarStatement cp = (CopyVarStatement) root;
					if(cp.getVariable().getLocal().isStack()) {
						return use;
					}
				}
			}
			Expression rhs = def.getExpression();
			if(rhs instanceof ConstantExpression) {
				return handleConstant(def, use, (ConstantExpression) rhs);
			} else if(rhs instanceof VarExpression) {
				return handleVar(def, use, (VarExpression) rhs);
			} else if (!(rhs instanceof CaughtExceptionExpression || rhs instanceof PhiExpression)) {
				return handleComplex(def, use);
			}
			return use;
		}

		// Attempt to inline the value of a var with its definition
		private Statement visitVar(VarExpression var) {
			CopyVarStatement def = localAccess.defs.get(var.getLocal());
			return findSubstitution(root, def, var);
		}
		
		// Attempt to inline each value of the vars in the phi statement with their respective definitions
		private Statement visitPhi(PhiExpression phi) {
			for(HeaderStatement header : phi.headers()) {
				Expression e = phi.getLocal(header);
				if(e instanceof VarExpression) {
					CopyVarStatement def = localAccess.defs.get(((VarExpression) e).getLocal());
					if(def.getExpression() instanceof VarExpression) {
						Statement e1 = findSubstitution(phi, def, (VarExpression) e);
						if(e1 != null && e1 != e) {
							phi.setLocal(header, (Expression) e1);
							change = true;
						}
					}
				}
			}
			return phi;
		}

		// Process a statement, switching control flow based on the statement's type.
		@Override
		public Statement visit(Statement stmt) {
			if(stmt instanceof VarExpression) {
				return choose(visitVar((VarExpression) stmt), stmt);
			} else if(stmt instanceof PhiExpression) {
				return choose(visitPhi((PhiExpression) stmt), stmt);
			}
			return stmt;
		}
		
		// Returns the non-null one of the two parameters passed
		private Statement choose(Statement e, Statement def) {
			if(e != null) {
				return e;
			} else {
				return def;
			}
		}
		
		/**
		 * A statement is uncopyable if duplication of statement will change the semantics of the program.
		 * @param stmt Statement to check for uncopyability.
		 * @return Whether the statement is uncopyable.
		 */
		private boolean isUncopyable(Statement stmt) {
			for(Statement s : Statement.enumerate(stmt)) {
				if(UNCOPYABLE.contains(s.getClass())) {
					return true;
				}
			}
			return false;
		}
		
		/**
		 * A statement is untransferable if the location of the statement impacts the semantics of the program
		 * @param use Statement that the variable is being used in
		 * @param tail Variable that will get inlined
		 * @param def Definition of variable that is being checked for transferability
		 * @return Whether the statement is uncopyable.
		 */
		private boolean canTransferToUse(Statement use, Statement tail, CopyVarStatement def) {
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
					return true;
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
					return false;
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
									_break();
								}
							}
							return s;
						}
					}.visit();
					canPropagate = canPropagate2.get();
					
					if(!canPropagate) {
						return false;
					}
				}
			}
			
			if(canPropagate) {
				return true;
			} else {
				return false;
			}
		}
		
		public boolean changed() {
			return change;
		}
		
		// Selects a statement to be processed.
		@Override
		public void reset(Statement stmt) {
			super.reset(stmt);
			change = false;
		}
		
		@Override
		protected void visited(Statement stmt, Statement node, int addr, Statement vis) {
			if(vis != node) {
				stmt.overwrite(vis, addr);
				change = true;
			}
			verify();
		}
		
		private void verify() {
			SSALocalAccess fresh = new SSALocalAccess(code);
			
			Set<VersionedLocal> keySet = new HashSet<>(fresh.useCount.keySet());
			keySet.addAll(localAccess.useCount.keySet());
			List<VersionedLocal> sortedKeys = new ArrayList<>(keySet);
			Collections.sort(sortedKeys);
			
			String message = null;
			for(VersionedLocal e : sortedKeys) {
				AtomicInteger i1 = fresh.useCount.get(e);
				AtomicInteger i2 = localAccess.useCount.get(e);
				if(i1 == null) {
					message = "Real no contain: " + e + ", other: " + i2.get();
				} else if(i2 == null) {
					message = "Current no contain: " + e + ", other: " + i1.get();
				} else if(i1.get() != i2.get()) {
					message = "Mismatch: " + e + " " + i1.get() + ":" + i2.get();
				}
			}
			
			if(message != null) {
				throw new RuntimeException(message + "\n" + code.toString());
			}
		}
	}
}
