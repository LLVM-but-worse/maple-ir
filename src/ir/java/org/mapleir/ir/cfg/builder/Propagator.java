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
import org.mapleir.ir.code.stmt.PopStatement;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStatement;
import org.mapleir.ir.code.stmt.copy.CopyPhiStatement;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.VersionedLocal;
import org.mapleir.stdlib.cfg.edge.TryCatchEdge;
import org.mapleir.stdlib.cfg.util.TypeUtils;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.SetCreator;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;
import org.mapleir.stdlib.ir.StatementVisitor;
import org.mapleir.stdlib.ir.transform.ssa.SSALocalAccess;

public class Propagator extends OptimisationPass.Optimiser {

	private static final Set<Class<? extends Statement>> UNCOPYABLE = new HashSet<>();
	
	static {
		UNCOPYABLE.add(InvocationExpression.class);
		UNCOPYABLE.add(DynamicInvocationExpression.class);
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
			
			for(BasicBlock r : builder.graph.wanderAllTrails(b)) {
				res.addAll(r);
			}
			
			return res;
		}
		
		private Expression findRootDefinition(VarExpression v) {
			Local l = v.getLocal();
			AbstractCopyStatement def = localAccess.defs.get(l);
			Expression e = def.getExpression();
			
			int opcode = e.getOpcode();
			if(opcode == Opcode.LOCAL_LOAD) {
				VarExpression v2 = (VarExpression) e;
				if(v2.getLocal() != l) {
					return findRootDefinition(v2);
				}
			}
			
			return e;
		}
		
		private boolean areEquivalent(PhiExpression p, PhiExpression q) {
			Set<BasicBlock> sources = new HashSet<>();
			Set<BasicBlock> psrc = p.getSources();
			Set<BasicBlock> qsrc = q.getSources();
			
			sources.addAll(p.getSources());
			sources.addAll(q.getSources());

			if(sources.size() != psrc.size() || sources.size() != qsrc.size()) {
				return false;
			}

			for(BasicBlock b : sources) {
				Expression e1 = p.getArgument(b);
				Expression e2 = q.getArgument(b);
				if(e1 == null || e2 == null) {
					return false;
				}
				
				if(e1.getOpcode() == Opcode.LOCAL_LOAD) {
					e1 = findRootDefinition((VarExpression) e1);
				}
				if(e2.getOpcode() == Opcode.LOCAL_LOAD) {
					e2 = findRootDefinition((VarExpression) e2);
				}
				
				if(!e1.equivalent(e2)) {
					return false;
				}
			}

			return true;
		}
		
		private Set<List<CopyPhiStatement>> buildEquivalencyMap(List<CopyPhiStatement> phis) {
			Set<List<CopyPhiStatement>> equiv = new HashSet<>();
			Set<CopyPhiStatement> resolved = new HashSet<>();
			
			/* For each phi, p:
			 *   we check all of the other phis that have
			 *   not yet been resolved and if we find any
			 *   phis that are equivalent, we map them to
			 *   p. so that we are able to */
			for(CopyPhiStatement cps : phis) {
				if(resolved.contains(cps)) {
					continue;
				}
				resolved.add(cps);
				
				List<CopyPhiStatement> res = new ArrayList<>();
				res.add(cps);

				PhiExpression p = cps.getExpression();
				
				for(CopyPhiStatement cps2 : phis) {
					if(cps != cps2 && !resolved.contains(cps2)) {
						PhiExpression q = cps2.getExpression();
						if(areEquivalent(p, q)) {
							res.add(cps2);
							resolved.add(cps2);
						}
					}
				}
				
				equiv.add(res);
			}
			
			return equiv;
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
					Set<List<CopyPhiStatement>> equiv = buildEquivalencyMap(phis);

					for(List<CopyPhiStatement> e : equiv) {
						if(e.size() <= 1)
							continue;
						
						// key should be earliest
						// remove vals from code and replace use of val vars with key var
						
						// choose which phi to keep.
						// favour lvars.

						
						CopyPhiStatement keepPhi = null;
						
						for(CopyPhiStatement cps : e) {
							if(!cps.getVariable().getLocal().isStack()) {
								keepPhi = cps;
								break;
							}
						}
						
						if(keepPhi == null) {
							keepPhi = e.get(0);
						}
						
						Set<CopyPhiStatement> useless = new HashSet<>(e);
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
							for(Statement s : reachable.enumerate()) {
								if(s.getOpcode() == Opcode.LOCAL_LOAD) {
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
//					interweavingPhis(b, phis);
				}
			}
			return change;
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
					if(!def.isSynthetic() && op != Opcode.CATCH && op != Opcode.EPHI) {
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
			for(Statement s : stmt.enumerate()) {
				if(s.getOpcode() == Opcode.LOCAL_LOAD) {
					unuseLocal(((VarExpression) s).getLocal());
				}
			}
		}
		
		private void print(Statement stmt) {
			for(Statement c : stmt.enumerate()) {
				System.err.println("  " + c + " type: " + c.getClass().getSimpleName());
				System.err.println("      par: " + c.getParent());
			}
		}
		
		private void copied(Statement stmt) {
			for(Statement s : stmt.enumerate()) {
				if(s.getOpcode() == Opcode.LOCAL_LOAD) {
					try {
						reuseLocal(((VarExpression) s).getLocal());
					} catch(RuntimeException e) {
						System.err.println("Copy: " + stmt);
						print(stmt);
						System.err.println("l: " + ((VarExpression) s).getLocal());
						throw e;
					}
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
//			System.out.println("killded: " + def);
			def.getBlock().remove(def);
			Local local = def.getVariable().getLocal();
			localAccess.useCount.remove(local);
			localAccess.defs.remove(local);
			
//			System.out.println("DEAD(1): " + def);
		}
		
		private int uses(Local l) {
			if(localAccess.useCount.containsKey(l)) {
				return localAccess.useCount.get(l).get();
			} else {
				throw new IllegalStateException("Local " + l + " not in useCount map. Def: " + localAccess.defs.get(l));
			}
		}

		private int _xuselocal(Local l, boolean re) {
			if(localAccess.useCount.containsKey(l)) {
				if(re) {
					return localAccess.useCount.get(l).incrementAndGet();
				} else {
					return localAccess.useCount.get(l).decrementAndGet();
				}
			} else {
				System.err.println(builder.graph);
				throw new IllegalStateException("Local " + l + " not in useCount map. Def: " + localAccess.defs.get(l));
			}
		}
		
		private int unuseLocal(Local l) {
			return _xuselocal(l, false);
		}
		
		private int reuseLocal(Local l) {
			return _xuselocal(l, true);
		}
		
		private Expression handleConstant(AbstractCopyStatement def, VarExpression use, ConstantExpression rhs) {
			// x = 7;
			// use(x)
			//         goes to
			// x = 7
			// use(7)
			
			// localCount -= 1;
			unuseLocal(use.getLocal());
			return rhs.copy();
		}

		private Expression handleVar(AbstractCopyStatement def, VarExpression use, VarExpression rhs) {
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

		private Expression handleComplex(AbstractCopyStatement def, VarExpression use) {
			if(!canTransferToUse(root, use, def)) {
				// System.out.println("Refuse to propagate " + def + " into " + use.getRootParent());
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
					propagatee.unlink();
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
						return propagatee.copy();
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
					return propagatee.copy();
				}
			}
			return null;
		}
		
		private Expression findSubstitution(Statement root, AbstractCopyStatement def, VarExpression use) {
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
			
			Expression ret = use;
			
			if(opcode == Opcode.CONST_LOAD) {
				ret =  handleConstant(def, use, (ConstantExpression) rhs);
			} else if(opcode == Opcode.LOCAL_LOAD) {
				ret =  handleVar(def, use, (VarExpression) rhs);
			} else if(opcode != Opcode.CATCH && opcode != Opcode.PHI && opcode != Opcode.EPHI) {
				BasicBlock db = def.getBlock();
				BasicBlock ub = root.getBlock();
				
				List<ExceptionRange<BasicBlock>> dr = db.getProtectingRanges();
				List<ExceptionRange<BasicBlock>> ur = ub.getProtectingRanges();
				
				int drs = dr.size(), urs = ur.size();
				
				boolean transferable = false;
				
				if(drs > 0) {
					if(urs == 0) {
						// we can clone the range information.
						for(ExceptionRange<BasicBlock> e : dr) {
							e.addVertex(ub);
							builder.graph.addEdge(ub, new TryCatchEdge<>(ub, e));
						}
						
						transferable = true;
					} else {
						dr.removeAll(ur);
						
						if(dr.size() == 0) {
							transferable = true;
						}
					}
				} else if(urs == 0) {
					transferable = true;
				}
				
				if(transferable) {
					ret = handleComplex(def, use);
//					if(ret != null) {
//						System.out.println("^ def: " + def);
//						System.out.println("  {{" + root + "}} " + ub);
//					}
				}
			}
			return ret;
		}

		private Expression visitVar(VarExpression var) {
			AbstractCopyStatement def = localAccess.defs.get(var.getLocal());
			Expression e = findSubstitution(root, def, var);
			if(e != null) {
				if(!var.getType().equals(e.getType())) {
					System.err.println();
					System.err.println(builder.graph);
					System.err.println();
					System.err.println("Root: " + var.getRootParent().getId() + ". " + var.getRootParent());
					System.err.println("Def: " + def.getId() + ". " + def);
					System.err.println(" var: " + var);
					System.err.println(" sub: " + e);
					System.err.println(" vt: " + var.getType());
					System.err.println(" st: " + e.getType());
					System.err.println(" v1: " + var.getLocal());
					System.err.println(" v1: " + def.getVariable().getLocal());
					System.err.println(" hc1: " + var.getLocal().hashCode());
					System.err.println(" hc2: " + def.getVariable().getLocal().hashCode());
					System.err.println();
					System.err.println();
					throw new RuntimeException();
				}
			}
			return e;
		}
		
		private PhiExpression visitPhi(PhiExpression phi) {
			for(BasicBlock s : phi.getSources()) {
				Expression e = phi.getArgument(s);
				
				if(e.getOpcode() == Opcode.LOCAL_LOAD) {
					VarExpression use = (VarExpression) e;
					Local ul = use.getLocal();
					
					AbstractCopyStatement def = localAccess.defs.get(ul);
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
//						ConstantExpression c = (ConstantExpression) rhs;
//						if(c.getConstant() != null) {
//							cand = (VarExpression) e;
//						}
						// if(ul.isStack()) {
//							cand = (VarExpression) e;
						// }
					}
					
					if(cand != null) {
						Expression sub = findSubstitution(phi, def, (VarExpression) e);
						if(sub != null && sub != e) {
							if(e != null) {
								if(!TypeUtils.asSimpleType(sub.getType()).equals(TypeUtils.asSimpleType(e.getType()))) {
									throw new RuntimeException(String.format("[%s]%s, [%s]%s, t1:%s, t2:%s", sub.getOpname(), sub, e.getOpname(), e, sub.getType(), e.getType()));
								}
							}
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
				VarExpression v = (VarExpression) stmt;
				return choose(visitVar(v), v);
			} else if(stmt.getOpcode() == Opcode.PHI || stmt.getOpcode() == Opcode.EPHI) {
				PhiExpression phi = (PhiExpression) stmt;
				return choose(visitPhi(phi), phi);
			}
			return stmt;
		}
		
		private Statement choose(Expression e, Expression def) {
			if(e != null) {
				return e;
			} else {
				return def;
			}
		}
		
		private boolean isUncopyable(Statement stmt) {
			for(Statement s : stmt.enumerate()) {
				if(UNCOPYABLE.contains(s.getClass())) {
					return true;
				}
			}
			return false;
		}
		
		private boolean isInvoke(Statement e) {
			int opcode = e.getOpcode();
			return opcode == Opcode.INVOKE || opcode == Opcode.DYNAMIC_INVOKE || opcode == Opcode.INIT_OBJ;
		}
		
		private boolean determineKill(Statement use, Statement tail, AbstractCopyStatement def, 
				AtomicBoolean invoke, AtomicBoolean array, Set<String> fieldsUsed, Statement s) {
			
			if(s.getOpcode() == Opcode.FIELD_LOAD) {
				if(invoke.get()) {
					return false;
				}
			} else if(s.getOpcode() == Opcode.FIELD_STORE) {
				if(invoke.get()) {
					return false;
				} else if(fieldsUsed.size() > 0) {
					FieldStoreStatement store = (FieldStoreStatement) s;
					String key = store.getName() + "." + store.getDesc();
					if(fieldsUsed.contains(key)) {
						return false;
					}
				}
			} else if(s.getOpcode() == Opcode.ARRAY_STORE) {
				if(invoke.get() || array.get()) {
					return false;
				}
			} else if(s.getOpcode() == Opcode.MONITOR) {
				if(invoke.get()) {
					return false;
				}
			} else if(isInvoke(s)) {
				if(invoke.get() || fieldsUsed.size() > 0 || array.get()) {
					return false;
				}
			}
			
			return true;
		}
		
		private boolean canTransferToUse(Statement use, Statement tail, AbstractCopyStatement def) {
			Local local = def.getVariable().getLocal();
			Expression rhs = def.getExpression();
			
			Set<String> fieldsUsed = new HashSet<>();
			AtomicBoolean invoke = new AtomicBoolean();
			AtomicBoolean array = new AtomicBoolean();
			
			if(rhs.getOpcode() == Opcode.FIELD_LOAD) {
				FieldLoadExpression fl = (FieldLoadExpression) rhs;
				fieldsUsed.add(fl.getName() + "." + fl.getDesc());
			} else if(isInvoke(rhs)) {
				invoke.set(true);
			} else if(rhs.getOpcode() == Opcode.ARRAY_LOAD) {
				array.set(true);
			} else if(rhs.getOpcode() == Opcode.CONST_LOAD) {
				return true;
			}
			
			new StatementVisitor(rhs) {
				@Override
				public Statement visit(Statement stmt) {
					if(stmt.getOpcode() == Opcode.FIELD_LOAD) {
						FieldLoadExpression fl = (FieldLoadExpression) stmt;
						fieldsUsed.add(fl.getName() + "." + fl.getDesc());
					} else if(isInvoke(stmt)) {
						invoke.set(true);
					} else if(stmt.getOpcode() == Opcode.ARRAY_LOAD) {
						array.set(true);
					}
					return stmt;
				}
			}.visit();
			
//			System.out.println("Def: " + def);
//			if(def.toString().equals("svar0_0 = new java.lang.StringBuilder();")) {
//				System.err.println("Invoke: " + invoke.get());
//				System.err.println("Array: " + array.get());
//				System.err.println("FS: " + fieldsUsed);
//				System.err.println(builder.graph);
//				throw new RuntimeException();
//			}
			
			Set<Statement> path = findReachable(def, use);
			path.remove(def);
			path.add(use);
			
			boolean canPropagate = true;
			
			for(Statement stmt : path) {
				if(stmt != use) {
					for(Statement s : stmt.enumerate()) {
						if(determineKill(use, tail, def, invoke, array, fieldsUsed, s)) {
							return false;
						}
					}
				} else {
					// the root here must be the 'use' Statement.
					AtomicBoolean canPropagate2 = new AtomicBoolean(canPropagate);
					
					if(invoke.get() || array.get() || !fieldsUsed.isEmpty()) {
						for(Statement s : stmt.execEnumerate()) {
							if(s == tail && (s.getOpcode() == Opcode.LOCAL_LOAD && ((VarExpression) s).getLocal() == local)) {
								break;
							} else {
								if((isInvoke(s)) || (invoke.get() && (s instanceof FieldStoreStatement || s instanceof ArrayStoreStatement))) {
									canPropagate2.set(false);
									// System.out.println("  KILL: " + s);
									break;
								}
							}
						}
						
						canPropagate = canPropagate2.get();
					}
				}
				
				if(!canPropagate) {
					return false;
				}
			}
			
			return canPropagate;
		}
		
		@SuppressWarnings("unused")
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
		
		public boolean changed() {
			return change;
		}
		
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
				
//				for (BasicBlock b : builder.graph.vertices()) {
//					for (Statement s : b) {
//						s.spew("");
//						try {
//							s.checkConsistency();
//						} catch (RuntimeException e) {
//							throw new RuntimeException(s.toString() + " in #" + b.getId() + "\n  After " + node, e);
//						}
//					}
//				}
			}
//			verify();
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
				if(i1 == null && i2.get() != 0) {
					message = "Real no contain: " + e + ", other: " + i2.get();
				} else if(i2 == null && i1.get() != 0) {
					message = "Current no contain: " + e + ", other: " + i1.get();
				} else if(i1 != null && i2 != null && i1.get() != i2.get()) {
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
		}
		
		if(visitor.cleanEquivalentPhis()) {
			changes++;
		}
		
		return changes;
	}
}