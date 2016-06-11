package org.rsdeob.stdlib.cfg.ir.transform.impl;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.rsdeob.stdlib.cfg.ir.Local;
import org.rsdeob.stdlib.cfg.ir.RootStatement;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementVisitor;
import org.rsdeob.stdlib.cfg.ir.expr.ArrayLoadExpression;
import org.rsdeob.stdlib.cfg.ir.expr.ConstantExpression;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.FieldLoadExpression;
import org.rsdeob.stdlib.cfg.ir.expr.NewArrayExpression;
import org.rsdeob.stdlib.cfg.ir.expr.UninitialisedObjectExpression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.PopStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.ir.stat.SyntheticStatement;
import org.topdank.banalysis.filter.Filter;

public class NewValuePropagator {
	
	private final RootStatement root;
	private final StatementGraph graph;
	private DefinitionAnalyser definitions;
	private LivenessAnalyser liveness;
	private int changedStmts;
	
	public NewValuePropagator(RootStatement root, StatementGraph graph) {
		this.root = root;
		this.graph = graph;
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
						continue;
					}
				}
				
				Statement newStmt = stmt.copy();
				
				Transformer transformer = new Transformer(stmt, newStmt);
				transformer.visit();
				
				if(transformer.change) {
					change.set(true);
					
					definitions.update(stmt, newStmt);
					definitions.remove(stmt);
					liveness.update(stmt, newStmt);
					liveness.remove(stmt);
					
					graph.replace(stmt, newStmt);
					root.overwrite(newStmt, root.indexOf(stmt));
					
					definitions.processQueue();
					liveness.processQueue();
				}
			}
			
			if(!change.get()) {
				break;
			}
		}
	}
	
	public int process(DefinitionAnalyser definitions, LivenessAnalyser liveness) {
		this.definitions = definitions;
		this.liveness = liveness;
		processImpl();
		return changedStmts;
	}
	
	private class Transformer extends StatementVisitor {

		private final Map<Local, Set<CopyVarStatement>> reachingDefs;
		private Statement orig;
		private boolean change;

		public Transformer(Statement orig, Statement copy) {
			super(copy);
			this.orig = orig;
			reachingDefs = definitions.in(orig);
		}

		@Override
		protected void visited(Statement stmt, Statement node, int addr, Statement vis) {
			super.visited(stmt, node, addr, vis);

			if (node != vis) {
				// System.out.printf("not_equal: addr=%d, old:%s, new:%s, root:%s.%n", addr, node, vis, root);
				changedStmts++;
				change = true;
			}
		}
		
		private boolean canLivePropagate(Statement s, Local local) {
			if(!liveness.out(s).containsKey(local)) {
				return true;
			} else {
				if(liveness.out(s).get(local)) {
					if(s instanceof CopyVarStatement) {
						CopyVarStatement copy = (CopyVarStatement) s;
						if(copy.getVariable().getLocal() == local) {
							return true;
						} else {
							return false;
						}
					} else {
						return false;
					}
				} else {
					return false;
				}
			}
		}

		@Override
		public Statement visit(Statement _s) {
			if(_s instanceof VarExpression) {
				// VarExpression var = (VarExpression) _s;
				VarExpression vExpr = (VarExpression) _s;
				Local var = vExpr.getLocal();
				Set<CopyVarStatement> defs = reachingDefs.get(var);
				if(defs.size() == 1) {
					CopyVarStatement def = defs.iterator().next();
					Expression rhs = def.getExpression();
					
					if(rhs instanceof VarExpression) {
						// rhs cannot be defined inbetween def and s
						Statement head = def;
						Statement tail = vExpr;
						VerifierVisitor vis = new VerifierVisitor(NewValuePropagator.this.root, head, tail, new VarKillFilter((VarExpression) rhs));
						vis.visit();
						if (!vis.valid) {
							return vExpr;
						}
						return rhs;
					} else if(rhs instanceof ConstantExpression) {
						return rhs;
					} else if(rhs instanceof FieldLoadExpression) {
						Statement head = def;
						Statement tail = vExpr;
						VerifierVisitor vis = new VerifierVisitor(NewValuePropagator.this.root, head, tail, new AffectorFilter(rhs));
						vis.visit();
						if(vis.valid && canLivePropagate(orig, var)) {
							return rhs;
						}
					} else if(rhs instanceof ArrayLoadExpression) {
						Statement head = def;
						Statement tail = vExpr;
						VerifierVisitor vis = new VerifierVisitor(NewValuePropagator.this.root, head, tail, new AffectorFilter(rhs));
						vis.visit();
						if(vis.valid && canLivePropagate(orig, var)) {
							return rhs;
						}
					} else if(rhs instanceof NewArrayExpression) {
						Statement head = rhs;
						Statement tail = vExpr;
						VerifierVisitor vis = new VerifierVisitor(NewValuePropagator.this.root, head, tail, new VarUseFilter(vExpr));
						vis.visit();
						System.out.println("var: " + var +"  at " + root);
						if(vis.valid && !liveness.out(orig).containsKey(var)) {
							return rhs;
						}
					} else if(rhs instanceof UninitialisedObjectExpression) {
						Statement head = rhs;
						Statement tail = vExpr;
						VerifierVisitor vis = new VerifierVisitor(NewValuePropagator.this.root, head, tail, new VarKillFilter(vExpr));
						vis.visit();
						if(vis.valid && !liveness.out(orig).containsKey(var)) {
							return rhs;
						}
					}
				} else {
					// find a common local def
					Set<Local> actualVars = new HashSet<>();
					for(CopyVarStatement cvs : defs) {
						Local rhsVar = null;
						if(cvs.getExpression() instanceof VarExpression) {
							VarExpression ve = (VarExpression) cvs.getExpression();
							rhsVar = ve.getLocal();
							actualVars.add(rhsVar);
						}
					}
					for(CopyVarStatement cvs : defs) {
						Expression expr = cvs.getExpression();
						if(expr instanceof VarExpression) {
							VarExpression ve = (VarExpression) expr;
							Local rhsLocal = ve.getLocal();
							Map<Local, Set<CopyVarStatement>> rhsDefsMap = definitions.in(cvs);
							Set<CopyVarStatement> rhsDefs = rhsDefsMap.get(rhsLocal);
							if(rhsDefs.size() > 1) {
								actualVars.remove(rhsLocal);
							} else {
//								CopyVarStatement rhsDef = rhsDefs.iterator().next();
//								System.out.println("  def(1): " + cvs);
//								System.out.println("  rhsDef(1): " + rhsDef);
//								if(!rhsDef.getExpression().equivalent(cvs.getExpression())) {
//									System.out.println("      not equal " + rhsDef);
//									System.out.println("            to  " + cvs);
//									actualVars.remove(rhsLocal);
//								}
							}
						} else if(expr instanceof ConstantExpression) {
							ConstantExpression c = (ConstantExpression) expr;
							Map<Local, Set<CopyVarStatement>> rhsDefsMap = definitions.in(cvs);
							Iterator<Local> it = actualVars.iterator();
							while(it.hasNext()) {
								Local possible = it.next();
								Set<CopyVarStatement> possibleDefs = rhsDefsMap.get(possible);
								if(possibleDefs.size() > 1) {
									actualVars.remove(possible);
								} else {
									CopyVarStatement rhsDef = possibleDefs.iterator().next();
									if(!c.equivalent(rhsDef.getExpression())) {
										it.remove();
									}
								}
							}
						} else {
							actualVars.remove(cvs.getVariable().getLocal());
						}
					}

					if(actualVars.size() == 1) {
//						vExpr.setLocal(actualVars.iterator().next());
//						System.out.println("_actual " + _s + " | " + root + " = " + defs +" = " + actualVars);
//						return new VarExpression(actualVars.iterator().next(), vExpr.getType());
					}
				}
			}
			return _s;
		}
	}
	
	private class VerifierVisitor extends StatementVisitor {
		
		private final Statement head;
		private final Statement tail;
		private final Filter<Statement> filter;
		private boolean valid;
		private boolean start;
		
		public VerifierVisitor(RootStatement root, Statement head, Statement tail, Filter<Statement> filter) {
			super(root);
			this.head = head;
			this.tail = tail;
			this.filter = filter;
			valid = true;
		}
		
		@Override
		public final Statement visit(Statement s) {
			if(!start) {
				if(s == head) {
					start = true;
				}
			} else {
				if(s == tail) {
					_break();
					return s;
				}
				
				if(filter.accept(s)) {
					valid = false;
					_break();
					return s;
				}
			}
			return s;
		}
	}
	
//	static final class ArrayKillFilter implements Filter<Statement> {
//		final ArrayLoadExpression expr;
//		final int index;
//		
//		ArrayKillFilter(ArrayLoadExpression expr) {
//			this.expr = expr;
//			if(expr.getIndexExpression() instanceof ConstantExpression) {
//				index = (Integer) ((ConstantExpression) expr.getIndexExpression()).getConstant();
//			} else {
//				index = -1;
//			}
//		}
//		
//		@Override
//		public boolean accept(Statement t) {
//			if(t instanceof InvocationExpression) {
//				return true;
//			} else if(t instanceof ArrayStoreStatement) {
//				ArrayStoreStatement store = (ArrayStoreStatement) t;
//				if(store.getIndexExpression() instanceof ConstantExpression) {
//					int index = (Integer) ((ConstantExpression) store.getIndexExpression()).getConstant();
//					
//				}
//			}
//			return false;
//		}
//	}
	
	static final class AffectorFilter implements Filter<Statement> {
		final Expression expr;
		
		AffectorFilter(Expression expr) {
			this.expr = expr;
		}
		
		@Override
		public boolean accept(Statement t) {
			if(expr.isAffectedBy(t)) {
				return true;
			} else {
				return false;
			}
		}
	}
	
	static final class VarKillFilter implements Filter<Statement> {
		final VarExpression expr;
		
		VarKillFilter(VarExpression expr) {
			this.expr = expr;
		}
		
		@Override
		public boolean accept(Statement t) {
			if(t instanceof CopyVarStatement) {
				CopyVarStatement stmt = (CopyVarStatement) t;
				if(stmt.getVariable().toString().equals(expr.toString())) {
					// System.out.println("   overwrite: " + expr + " , stmt: " + stmt);
					return true;
				}
			}
			return false;
		}
	}
	
	static final class VarUseFilter implements Filter<Statement> {
		final VarExpression expr;
		
		VarUseFilter(VarExpression expr) {
			this.expr = expr;
		}
		
		@Override
		public boolean accept(Statement t) {
			if(t instanceof VarExpression) {
				if(((VarExpression) t).getLocal() == expr.getLocal()) {
					// System.out.println("  use: " + t.getId() + ", " + t + " = " + expr.getId() +", " + expr);
					return true;
				}
			}
			return false;
		}
	}
}