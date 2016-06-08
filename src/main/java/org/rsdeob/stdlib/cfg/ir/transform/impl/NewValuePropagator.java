package org.rsdeob.stdlib.cfg.ir.transform.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.rsdeob.stdlib.cfg.ir.RootStatement;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementVisitor;
import org.rsdeob.stdlib.cfg.ir.expr.*;
import org.rsdeob.stdlib.cfg.ir.stat.ArrayStoreStatement;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.FieldStoreStatement;
import org.rsdeob.stdlib.cfg.ir.stat.PopStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.ir.stat.SyntheticStatement;
import org.topdank.banalysis.filter.Filter;

public class NewValuePropagator {

	static final class ArrayKillFilter implements Filter<Statement> {
		final ArrayLoadExpression expr;
		final int index;
		
		ArrayKillFilter(ArrayLoadExpression expr) {
			this.expr = expr;
			if(expr.getIndexExpression() instanceof ConstantExpression) {
				index = (Integer) ((ConstantExpression) expr.getIndexExpression()).getConstant();
			} else {
				index = -1;
			}
		}
		
		@Override
		public boolean accept(Statement t) {
			if(t instanceof InvocationExpression) {
				return true;
			} else if(t instanceof ArrayStoreStatement) {
				ArrayStoreStatement store = (ArrayStoreStatement) t;
				if(store.getIndexExpression() instanceof ConstantExpression) {
					int index = (Integer) ((ConstantExpression) store.getIndexExpression()).getConstant();
					
				}
			}
			return false;
		}
	}
	
	static final class FieldKillFilter implements Filter<Statement> {
		final FieldLoadExpression expr;
		
		FieldKillFilter(FieldLoadExpression expr) {
			this.expr = expr;
		}
		
		@Override
		public boolean accept(Statement t) {
			if(t instanceof InvocationExpression) {
				return true;
			} else if(t instanceof FieldStoreStatement) {
				FieldStoreStatement store = (FieldStoreStatement) t;
				if(store.getName().equals(expr.getName()) && store.getDesc().equals(expr.getDesc())) {
					return true;
				}
			}
			return false;
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
					System.out.println("   overwrite: " + expr + " , stmt: " + stmt);
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
				if(expr.toString().equals(t.toString())) {
					System.out.println("  use: " + t.getId() + ", " + t + " = " + expr.getId() +", " + expr);
					return true;
				}
			}
			return false;
		}
	}
	
	private final RootStatement root;
	private final StatementGraph graph;
	private DefinitionAnalyser definitions;
	private LivenessAnalyser liveness;
	
	public NewValuePropagator(RootStatement root, StatementGraph graph) {
		this.root = root;
		this.graph = graph;
	}
	
	private void processImpl() {
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
					
					System.out.println(root);
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
		return 0;
	}
	
	private class Transformer extends StatementVisitor {

		private final Map<String, Set<CopyVarStatement>> reachingDefs;
		private boolean change;

		public Transformer(Statement orig, Statement copy) {
			super(copy);
			reachingDefs = definitions.in(orig);
		}

		@Override
		protected void visited(Statement stmt, Statement node, int addr, Statement vis) {
			super.visited(stmt, node, addr, vis);

			if (node != vis) {
				change = true;
			}
		}

		@Override
		public Statement visit(Statement _s) {
			if(_s instanceof VarExpression) {
				VarExpression var = (VarExpression) _s;
				System.out.println("root: " + root.getId() +" , " + root + ", " + reachingDefs.get(var.toString()) + " , " + var);
				Set<CopyVarStatement> defs = reachingDefs.get(var.toString());
				if(defs.size() == 1) {
					CopyVarStatement def = defs.iterator().next();
					Expression rhs = def.getExpression();
					
					if(rhs instanceof VarExpression) {
//						Set<CopyVarStatement> rhsDefs = reachingDefs.get(rhs.toString());
//						System.out.println("   rhsdefs: " + rhsDefs);
//						if(rhsDefs.size() == 1) {
							// CopyVarStatement rhsDef = rhsDefs.iterator().next();
							// Expression realRhs = rhsDef.getExpression();
							
							// rhs cannot be defined inbetween def and s
							Statement head = def;
							Statement tail = var;
							VerifierVisitor vis = new VerifierVisitor(NewValuePropagator.this.root, head, tail, new VarKillFilter((VarExpression) rhs));
							vis.visit();
							if(!vis.valid) {
								return var;
							}
							return rhs;
//							if(realRhs instanceof VarExpression) {
//								
//							} else if(realRhs instanceof ConstantExpression) {
//								
//							} else if(realRhs instanceof FieldLoadExpression) {
//								
//							} else if(realRhs instanceof ArrayLoadExpression) {
//								
//							}
//						}
					} else if(rhs instanceof ConstantExpression) {
						return rhs.copy();
					} else if(rhs instanceof FieldLoadExpression) {
						Statement head = def;
						Statement tail = root;
						VerifierVisitor vis = new VerifierVisitor(NewValuePropagator.this.root, head, tail, new FieldKillFilter((FieldLoadExpression) rhs));
						vis.visit();
						if(vis.valid) {
							return rhs.copy();
						}
					} else if(rhs instanceof ArrayLoadExpression) {
						
					} else if(rhs instanceof NewArrayExpression) {
						Statement head = rhs;
						Statement tail = var;
						VerifierVisitor vis = new VerifierVisitor(NewValuePropagator.this.root, head, tail, new VarUseFilter(var));
						vis.visit();
						if(vis.valid) {
							return rhs;
						}
					} else if(rhs instanceof UninitialisedObjectExpression) {
						Statement head = rhs;
						Statement tail = var;
						VerifierVisitor vis = new VerifierVisitor(NewValuePropagator.this.root, head, tail, new VarKillFilter(var));
						vis.visit();
						if(vis.valid) {
							return rhs;
						}
					}
				} else {
					Set<VarExpression> actualVars = new HashSet<>();
					for(CopyVarStatement cvs : defs) {
						StatementVisitor vis = new StatementVisitor(cvs) {
							@Override
							public Statement visit(Statement s1) {
								if(s1 instanceof VarExpression) {
									
									for(VarExpression ve : actualVars) {
										if(ve.toString().equals(s1.toString())) {
											return s1;
										}
									}
									
									actualVars.add(var);
								}
								return s1;
							}
						};
						vis.visit();
						actualVars.remove(cvs.getVariable());
					}
					if(actualVars.size() == 1) {
						System.out.println("_actual " + _s + " | " + root + " = " + defs +" = " + actualVars);
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
}