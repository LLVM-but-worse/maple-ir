package org.mapleir.ir.cfg.builder;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.*;
import org.mapleir.ir.code.stmt.ArrayStoreStatement;
import org.mapleir.ir.code.stmt.FieldStoreStatement;
import org.mapleir.ir.code.stmt.MonitorStatement;
import org.mapleir.ir.code.stmt.PopStatement;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStatement;
import org.mapleir.ir.code.stmt.copy.CopyPhiStatement;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.ir.StatementVisitor;
import org.mapleir.stdlib.ir.transform.ssa.SSALocalAccess;

public class Propagator extends OptimisationPass.Optimiser {

	private static final Set<Class<? extends Statement>> UNCOPYABLE = new HashSet<>();
	
	static {
		UNCOPYABLE.add(InvocationExpression.class);
		UNCOPYABLE.add(UninitialisedObjectExpression.class);
		UNCOPYABLE.add(InitialisedObjectExpression.class);
	}

	private FeedbackStatementVisitor visitor;
	
	public Propagator(ControlFlowGraphBuilder builder, SSALocalAccess localAccess) {
		super(builder, localAccess);
		
		visitor = new FeedbackStatementVisitor(null);
	}
	
	class FeedbackStatementVisitor extends StatementVisitor {
		
		private boolean change = false;
		
		public FeedbackStatementVisitor(Statement root) {
			super(root);
		}
		
		private Set<Statement> _enumerate(Statement stmt) {
			Set<Statement> set = new HashSet<>();
			set.add(stmt);
			
			if(stmt.getOpcode() == Opcode.PHI_STORE) {
				CopyPhiStatement phi = (CopyPhiStatement) stmt;
				for(Expression e : phi.getExpression().getArguments().values()) {
					set.addAll(_enumerate(e));
				}
			} else {
				for(Statement s : stmt) {
					set.addAll(_enumerate(s));
				}
			}
			
			return set;
		}
		
		private Iterable<Statement> enumerate(Statement stmt) {
			return _enumerate(stmt);
		}

		private Set<Statement> findReachable(Statement from, Statement to) {
			Set<Statement> res = new HashSet<>();
			BasicBlock f = from.getBlock();
			BasicBlock t = to.getBlock();
			
			int end = f == t ? f.indexOf(to) : f.size();
			for(int i=f.indexOf(from); i < end; i++) {
				res.add(f.get(i));
			}
			
			if(f != t) {
				for(BasicBlock r : builder.graph.wanderAllTrails(f, t)) {
					res.addAll(r);
				}
			}
			
			return res;
		}
		
		private Set<Statement> findReachable(Statement stmt) {
			Set<Statement> res = new HashSet<>();
			BasicBlock b = stmt.getBlock();
			for(int i=b.indexOf(stmt); i < b.size(); i++) {
				res.add(b.get(i));
			}
			
			for(BasicBlock r : builder.graph.wanderAllTrails(b, builder.exit)) {
				res.addAll(r);
			}
			
			return res;
		}
		
		private boolean cleanEquivalentPhis() {
			boolean change = false;
						
			for(BasicBlock b : builder.graph.vertices()) {
				List<CopyPhiStatement> phis = new ArrayList<>();
				
				for(Statement stmt : b) {
					if(stmt.getOpcode() == Opcode.PHI_STORE) {
						phis.add((CopyPhiStatement) stmt);
					} else {
						break;
					}
				}
						
				if(phis.size() > 1) {
					NullPermeableHashMap<CopyPhiStatement, Set<CopyPhiStatement>> equiv = new NullPermeableHashMap<>(new SetCreator<>());
					for(CopyPhiStatement cps : phis) {
						if(equiv.values().contains(cps)) {
							continue;
						}
						PhiExpression phi = cps.getExpression();
						for(CopyPhiStatement cps2 : phis) {
							if(cps != cps2) {
								if(equiv.keySet().contains(cps2)) {
									continue;
								}
								PhiExpression phi2 = cps2.getExpression();
								if(phi.equivalent(phi2)) {
									equiv.getNonNull(cps).add(cps2);
								}
							}
						}
					}

					for(Entry<CopyPhiStatement, Set<CopyPhiStatement>> e : equiv.entrySet()) {					
						// key should be earliest
						// remove vals from code and replace use of val vars with key var
						
						// choose which phi to keep.
						// favour lvars.
						
						Set<CopyPhiStatement> all = new HashSet<>();
						all.add(e.getKey());
						all.addAll(e.getValue());
						
						CopyPhiStatement keepPhi = null;
						
						for(CopyPhiStatement cps : all) {
							if(!cps.getVariable().getLocal().isStack()) {
								keepPhi = cps;
								break;
							}
						}
						
						if(keepPhi == null) {
							keepPhi = e.getKey();
						}
						
						Set<CopyPhiStatement> useless = new HashSet<>(all);
						useless.remove(keepPhi);
						
						VersionedLocal phiLocal = (VersionedLocal) keepPhi.getVariable().getLocal();
						
						Set<VersionedLocal> toReplace = new HashSet<>();
						for(CopyPhiStatement def : useless) {
							VersionedLocal local = (VersionedLocal) def.getVariable().getLocal();
							toReplace.add(local);
							killed(def);
							b.remove(def);
						}
						
						// replace uses
						for(Statement reachable : findReachable(keepPhi)) {
							for(Statement s : reachable) {
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
						
						for(CopyPhiStatement def : useless) {
							Local local = def.getVariable().getLocal();
							localAccess.useCount.remove(local);
							localAccess.defs.remove(local);
						}
						change = true;
						
						phis.removeAll(useless);
					}
				}
				
				if(phis.size() > 1) {
					interweavingPhis(b, phis);
				}
			}
			return change;
		}
		
		private void interweavingPhis(BasicBlock bb, List<CopyPhiStatement> phis) {
			Map<Local, NullPermeableHashMap<BasicBlock, Set<Expression>>> reducedPcc = new HashMap<>();
			Map<Local, CopyPhiStatement> pdefs = new HashMap<>();
			
			for(CopyPhiStatement p : phis) {
				PhiExpression phi = p.getExpression();
				NullPermeableHashMap<BasicBlock, Set<Expression>> map = new NullPermeableHashMap<>(new SetCreator<>());
				
				for(Entry<BasicBlock, Expression> e : phi.getArguments().entrySet()) {
					Expression expr = e.getValue();
					if(expr.getOpcode() == Opcode.LOCAL_LOAD) {
						VarExpression v = (VarExpression) expr;
						// we don't want to store phi 
						AbstractCopyStatement def = localAccess.defs.get(v.getLocal());
						if(def.getOpcode() == Opcode.PHI_STORE) {
							continue;
						}
					}
					
					map.getNonNull(e.getKey()).add(expr);
				}

				Local l = p.getVariable().getLocal();
				reducedPcc.put(l, map);
				pdefs.put(l, p);
			}
		}
		
		private boolean cleanDead() {
			boolean changed = false;
			Iterator<Entry<VersionedLocal, AtomicInteger>> it = localAccess.useCount.entrySet().iterator();
			while(it.hasNext()) {
				Entry<VersionedLocal, AtomicInteger> e = it.next();
				if(e.getValue().get() == 0)  {
					AbstractCopyStatement def = localAccess.defs.get(e.getKey());
					Expression rhs = def.getExpression();
					int op = rhs.getOpcode();
					if(!def.isSynthetic() && op != Opcode.CATCH) {
						if(!fineBladeDefinition(def, it)) {
							killed(def);
							changed = true;
						}
					}
				}
			}
			return changed;
		}
		
		private void killed(Statement stmt) {
			for(Statement s : enumerate(stmt)) {
				if(s.getOpcode() == Opcode.LOCAL_LOAD) {
					unuseLocal(((VarExpression) s).getLocal());
				}
			}
		}
		
		private void copied(Statement stmt) {
			for(Statement s : enumerate(stmt)) {
				if(s.getOpcode() == Opcode.LOCAL_LOAD) {
					reuseLocal(((VarExpression) s).getLocal());
				}
			}
		}
		
		private boolean fineBladeDefinition(AbstractCopyStatement def, Iterator<?> it) {
			it.remove();
			Expression rhs = def.getExpression();
			BasicBlock b = def.getBlock();
			if(isUncopyable(rhs)) {
				rhs.unlink();
				PopStatement pop = new PopStatement(rhs);
				b.set(b.indexOf(def), pop);
				return true;
			} else {
				// easy remove
				b.remove(def);
				Local local = def.getVariable().getLocal();
				localAccess.useCount.remove(local);
				return false;
			}
		}
		
		private void scalpelDefinition(AbstractCopyStatement def) {
			def.getBlock().remove(def);
			Local local = def.getVariable().getLocal();
			localAccess.useCount.remove(local);
			localAccess.defs.remove(local);
		}
		
		private int uses(Local l) {
			if(localAccess.useCount.containsKey(l)) {
				return localAccess.useCount.get(l).get();
			} else {
				throw new IllegalStateException("Local " + l + " not in useCount map. Def: " + localAccess.defs.get(l));
			}
		}

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
		
		private void unuseLocal(Local l) {
			_xuselocal(l, false);
		}
		
		private void reuseLocal(Local l) {
			_xuselocal(l, true);
		}
		
		private Statement handleConstant(AbstractCopyStatement def, VarExpression use, ConstantExpression rhs) {
			// x = 7;
			// use(x)
			//         goes to
			// x = 7
			// use(7)
			
			// localCount -= 1;
			unuseLocal(use.getLocal());
			return rhs.copy();
		}

		private Statement handleVar(AbstractCopyStatement def, VarExpression use, VarExpression rhs) {
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

		private Statement handleComplex(AbstractCopyStatement def, VarExpression use) {
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
		
		private Statement findSubstitution(Statement root, AbstractCopyStatement def, VarExpression use) {
			// n.b. if this is called improperly (i.e. unpropagatable def),
			//      then the code may be dirtied/ruined.
			Local local = use.getLocal();
			if(!local.isStack()) {
				if(root.getOpcode() == Opcode.LOCAL_STORE || root.getOpcode() == Opcode.PHI_STORE) {
					AbstractCopyStatement cp = (AbstractCopyStatement) root;
					if(cp.getVariable().getLocal().isStack()) {
						return use;
					}
				}
			}
			Expression rhs = def.getExpression();
			int opcode = rhs.getOpcode();
			if(opcode == Opcode.CONST_LOAD) {
				return handleConstant(def, use, (ConstantExpression) rhs);
			} else if(opcode == Opcode.LOCAL_LOAD) {
				return handleVar(def, use, (VarExpression) rhs);
			} else if(opcode != Opcode.CATCH && opcode != Opcode.PHI) {
				return handleComplex(def, use);
			}
			return use;
		}

		private Statement visitVar(VarExpression var) {
			AbstractCopyStatement def = localAccess.defs.get(var.getLocal());
			return findSubstitution(root, def, var);
		}
		
		private Statement visitPhi(PhiExpression phi) {
			for(BasicBlock s : phi.getSources()) {
				Expression e = phi.getArgument(s);
				
				if(e.getOpcode() == Opcode.LOCAL_LOAD) {
					VarExpression use = (VarExpression) e;
					
					AbstractCopyStatement def = localAccess.defs.get((use).getLocal());
					Expression rhs = def.getExpression();
					int opcode = rhs.getOpcode();

					VarExpression cand = null;
					
					if(opcode == Opcode.LOCAL_LOAD) {
						VarExpression v = (VarExpression) rhs;
						Local l = v.getLocal();
						Local deflhs = def.getVariable().getLocal();
						// we only want to propagate if;
						//  l.isStack() == deflhs.isStack();
						// or:
						//  use.isStack() && !defrhs.isStack();
						if((l.isStack() == deflhs.isStack()) || (!l.isStack() && deflhs.isStack())) {
							cand = (VarExpression) e;
						}
					} else if(opcode == Opcode.CONST_LOAD) {
//						cand = (VarExpression) e;
					}
					
					if(cand != null) {
						Statement sub = findSubstitution(phi, def, (VarExpression) e);
						if(sub != null && sub != e) {
							phi.setArgument(s, (Expression) sub);
							change = true;
						}
					}
				}
			}
			return phi;
		}

		@Override
		public Statement visit(Statement stmt) {
			if(stmt.getOpcode() == Opcode.LOCAL_LOAD) {
				return choose(visitVar((VarExpression) stmt), stmt);
			} else if(stmt.getOpcode() == Opcode.PHI) {
				return choose(visitPhi((PhiExpression) stmt), stmt);
			}
			return stmt;
		}
		
		private Statement choose(Statement e, Statement def) {
			if(e != null) {
				return e;
			} else {
				return def;
			}
		}
		
		private boolean isUncopyable(Statement stmt) {
			for(Statement s : enumerate(stmt)) {
				if(UNCOPYABLE.contains(s.getClass())) {
					return true;
				}
			}
			return false;
		}
		
		private boolean canTransferToUse(Statement use, Statement tail, AbstractCopyStatement def) {
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
			
			Set<Statement> path = findReachable(def, use);
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
			SSALocalAccess fresh = new SSALocalAccess(builder.graph);
			
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
				throw new RuntimeException(message + "\n" + builder.graph.toString());
			}
		}
	}

	boolean attemptPop(PopStatement pop) {
		Expression expr = pop.getExpression();
		if(expr instanceof VarExpression) {
			VarExpression var = (VarExpression) expr;
			localAccess.useCount.get(var.getLocal()).decrementAndGet();
			pop.getBlock().remove(pop);
			return true;
		} else if(expr instanceof ConstantExpression) {
			pop.getBlock().remove(pop);
			return true;
		}
		return false;
	}
	
	boolean attempt(Statement stmt, FeedbackStatementVisitor visitor) {
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
	
	
	@Override
	public int run(BasicBlock b) {
		int changes = 0;
		
		for(Statement stmt : new ArrayList<>(b)) {
			if(!b.contains(stmt)) {
				continue;
			}
			if(attempt(stmt, visitor)) {
				changes++;
			}
			if(visitor.cleanDead()) {
				changes++;
			}
			if(visitor.cleanEquivalentPhis()) {
				changes++;
			}
		}
		
		return changes;
	}
}