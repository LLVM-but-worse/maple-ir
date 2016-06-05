package org.rsdeob.stdlib.cfg.ir.transform.impl;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.rsdeob.stdlib.cfg.edge.FlowEdge;
import org.rsdeob.stdlib.cfg.ir.RootStatement;
import org.rsdeob.stdlib.cfg.ir.StatementGraph;
import org.rsdeob.stdlib.cfg.ir.StatementVisitor;
import org.rsdeob.stdlib.cfg.ir.expr.ConstantExpression;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.FieldLoadExpression;
import org.rsdeob.stdlib.cfg.ir.expr.InvocationExpression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.FieldStoreStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.topdank.banalysis.filter.Filter;

public class NewValuePropagator {

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
					System.out.println("   vaR: " + expr + " , stmt: " + stmt);
					return true;
				}
			}
			return false;
		}
	}
	
	private final RootStatement root;
	private final StatementGraph graph;
	private DefinitionAnalyser definitions;
	
	public NewValuePropagator(RootStatement root, StatementGraph graph) {
		this.root = root;
		this.graph = graph;
	}
	
	private void processImpl() {
		while(true) {
			boolean change = false;
			
			for(Statement stmt : new HashSet<>(graph.vertices())) {
				Transformer impl = new Transformer(stmt);
				impl.visit();
//				definitions.update(stmt);
				if(impl.change) {
					change = true;
					break;
				}
			}
			
			if(!change) {
				break;
			} else {
				definitions = new DefinitionAnalyser(graph, root.getMethod());
				definitions.run();
			}
		}
	}
	
	public int process(DefinitionAnalyser definitions) {
		this.definitions = definitions;
		processImpl();
		return 0;
	}
	
	private class Transformer extends StatementVisitor {

		private final Map<String, Set<CopyVarStatement>> reachingDefs;
		private boolean change;

		public Transformer(Statement stmt) {
			super(stmt);
			reachingDefs = definitions.in(stmt);
		}

		@Override
		protected void visited(Statement stmt, Statement node, int addr, Statement vis) {
			super.visited(stmt, node, addr, vis);

			if (node != vis) {
				change = true;
			}
		}

		@Override
		public Statement visit(Statement s) {
			if(s instanceof VarExpression) {
				Set<CopyVarStatement> defs = reachingDefs.get(s.toString());
				VarExpression v = (VarExpression) s;
				
				if (defs != null && defs.size() == 1) {
					CopyVarStatement def = defs.iterator().next();
					Expression rhs = def.getExpression();
					if(rhs instanceof VarExpression) {
						if(((VarExpression) rhs).getIndex() != v.getIndex()) {
							Set<CopyVarStatement> rhsDefs = reachingDefs.get(def.getVariable().toString());
							if(rhsDefs.size() == 1) {
								CopyVarStatement rhsDef = rhsDefs.iterator().next();
								Expression rhsVal = rhsDef.getExpression();
								
								Filter<Statement> f = null;
								if(rhsVal instanceof FieldLoadExpression) {
									f = new FieldKillFilter((FieldLoadExpression) rhsVal);
								} else if(rhsVal instanceof VarExpression) {
									System.out.println(def.getId() + ", def: " + def + " " + root.getId() +", use: " + root);
									f = new VarKillFilter((VarExpression) rhsVal);
								} else if(rhsVal instanceof ConstantExpression) {
									_break();
									return rhsVal.copy();
								}
								
								if(f != null) {
									StatementVerifierVisitor vis = new StatementVerifierVisitor(NewValuePropagator.this.root, rhsVal, rhs, f);
									vis.visit();
									if(vis.valid) {
										_break();
										return rhsVal.copy();
									}
								}
							}
						}
					} else if(rhs instanceof ConstantExpression) {
						_break();
						return rhs.copy();
					} else if(rhs instanceof FieldLoadExpression) {
						FieldLoadExpression rhsVal = (FieldLoadExpression) rhs;
						StatementVerifierVisitor vis = new StatementVerifierVisitor(NewValuePropagator.this.root, rhsVal, v, new FieldKillFilter(rhsVal));
						vis.visit();
						if(vis.valid) {
							_break();
							return rhsVal.copy();
						}
					}
				}
			}
			return s;
		}
	}
	
	private class StatementVerifierVisitor extends StatementVisitor {
		
		protected final Statement tail;
		protected final Statement end;
		private final Filter<Statement> filter;
		protected boolean valid;
		private boolean start;
		
		public StatementVerifierVisitor(RootStatement root, Statement stmtTail, Statement end, Filter<Statement> filter) {
			super(root);
			tail = stmtTail;
			this.end = end;
			valid = true;
			this.filter = filter;
		}
		
		@Override
		public final Statement visit(Statement s) {
			if(!start) {
				if(s == tail) {
					start = true;
				}
			} else {
				if(s == end) {
					_break();
					return s;
				}
				Set<FlowEdge<Statement>> pes = graph.getReverseEdges(s);
				if (pes != null && pes.size() > 1) {
					_break();
					return s;
				}
				if(filter.accept(s)) {
					System.out.println("    filter acce pt ");
					valid = false;
					_break();
					return s;
				}
			}
			return s;
		}
	}
}