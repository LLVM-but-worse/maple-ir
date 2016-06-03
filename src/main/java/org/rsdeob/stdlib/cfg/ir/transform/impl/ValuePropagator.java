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
import org.rsdeob.stdlib.cfg.ir.expr.UninitialisedObjectExpression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.FieldStoreStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;

public class ValuePropagator {
	
	private final RootStatement root;
	private final StatementGraph graph;
	private DefinitionAnalyser definitions;
	
	public ValuePropagator(RootStatement root, StatementGraph graph) {
		this.root = root;
		this.graph = graph;
	}
	
	private void processImpl() {
		while(true) {
			boolean change = false;
			
			for(Statement stmt : new HashSet<>(graph.vertices())) {
				TransformerImpl impl = new TransformerImpl(stmt);
				impl.visit();
				if(impl.change) {
					change = true;
					break;
				}
			}
			
			if(!change) {
				break;
			}
		}
	}
	
	public int process(DefinitionAnalyser definitions) {
		this.definitions = definitions;
		processImpl();
		return 0;
	}
	
	private abstract class StatementVerifierVisitor extends StatementVisitor {
		
		protected final Statement tail;
		private final boolean redef;
		protected boolean flag;
		private boolean start;
		
		public StatementVerifierVisitor(RootStatement root, Statement tail) {
			super(root);
			this.tail = tail;
			flag = true;
			redef = !(tail instanceof VarExpression);
		}
		
		@Override
		public final Statement visit(Statement s) {
			if(!start) {
				if(s == tail) {
					start = true;
				}
			} else {
				// check only until the next block boundary.
				Set<FlowEdge<Statement>> pes = graph.getReverseEdges(s);
				if (pes != null && pes.size() > 1) {
					_break();
					return s;
				}
				if(redef && s instanceof VarExpression) {
					flag = false;
					_break();
					return s;
				}
				visitImpl(s);
			}
			return s;
		}
		
		abstract void visitImpl(Statement s);
	}
	
	private class VarVerifierImpl extends StatementVerifierVisitor {

		public VarVerifierImpl(RootStatement root, VarExpression tail) {
			super(root, tail);
		}

		@Override
		public void visitImpl(Statement s) {
			if (s instanceof CopyVarStatement) {
				CopyVarStatement cp = (CopyVarStatement) s;
				VarExpression cpv = cp.getVariable();
				if (cpv.toString().equals(tail.toString())) {
					flag = false;
				}
			}
		}			
	}
	
	private class FieldVerifierImpl extends StatementVerifierVisitor {

		public FieldVerifierImpl(RootStatement root, FieldLoadExpression tail) {
			super(root, tail);
		}

		@Override
		public void visitImpl(Statement s) {
			if(s.canChangeLogic()) {
				flag = false;
				_break();
				return;
			}
			
			if(s instanceof FieldStoreStatement) {
				// TODO: check hierarchy for correct class
				FieldStoreStatement store = (FieldStoreStatement) s;
				FieldLoadExpression load = (FieldLoadExpression) tail;
				
				if(load.isAffectedBy(store)) {
					flag = false;
					_break();
					return;
				}
			}
		}			
	}
	private class NewObjectVerifierImpl extends StatementVerifierVisitor {

		private final VarExpression expectedUse;
		
		public NewObjectVerifierImpl(RootStatement root, UninitialisedObjectExpression tail, VarExpression expectedUse) {
			super(root, tail);
			this.expectedUse = expectedUse;
		}

		@Override
		void visitImpl(Statement s) {
			if(s instanceof VarExpression) {
				if(s.toString().equals(expectedUse.toString())) {
					if(expectedUse != s) {
						flag = false;
					}
					_break();
				}
			}
		}
	}

	private class TransformerImpl extends StatementVisitor {

		private final Map<String, Set<CopyVarStatement>> reachingDefs;
		private boolean change;
		
		public TransformerImpl(Statement stmt) {
			super(stmt);
			reachingDefs = definitions.in(stmt);
		}
		
		@Override
		protected void visited(Statement stmt, Statement node, int addr, Statement vis) {
			super.visited(stmt, node, addr, vis);
			
			if(node != vis) {
				change = true;
			}
		}
		
		private Statement processVar(VarExpression v, VarExpression x) {
			/* situation:
			 *    1. v = x;
			 *       ...
			 *    n. use(v);
			 *    
			 *    in order to propagate x to the use of v, we have to 
			 *    verify that x is not redefined in between 1 and n.
			 */
			
			// find the tail statement, i.e. the 'x' bit in v = x;
			
			Set<CopyVarStatement> rhsDefs = reachingDefs.get(x.toString());
			if(rhsDefs.size() != 1) {
				return v;
			}
			CopyVarStatement rhsDef = rhsDefs.iterator().next();
			Expression tail = rhsDef.getExpression();
			
			// FIXME: is there a more generic way to do this?
			StatementVerifierVisitor impl = null;
			if(tail instanceof FieldLoadExpression) {
				impl = new FieldVerifierImpl(ValuePropagator.this.root, (FieldLoadExpression) tail);
			} else if(tail instanceof VarExpression) {
				impl = new VarVerifierImpl(ValuePropagator.this.root, (VarExpression) tail);
			} else if(tail instanceof UninitialisedObjectExpression) {
				// could the object be used before it is initialised?
				return x; // assume no
			} else {
				throw new UnsupportedOperationException(tail.toString());
			}
			
			impl.visit();
			if(impl.flag) {
				// can propagate
				return x;
			} else {
				// can't propagate
				return v;
			}
		}
		
		private Statement processNewObject(CopyVarStatement def, VarExpression v, UninitialisedObjectExpression e) {
			// we need to check if there is only 1 use of the
			// variable and if there is, then we can propagate it.
			
			NewObjectVerifierImpl impl = new NewObjectVerifierImpl(ValuePropagator.this.root, e, v);
			impl.visit();
			if(impl.flag) {
				// if we are propagating it, we must remove the previous
				//  v = newObj statement as the dead assignment eliminator 
				// refuses to remove these types of expressions;
				System.out.println("NEW " + def +  "   " + v +"   " + v.getId());
				System.out.println(ValuePropagator.this.root);
				ValuePropagator.this.root.delete(ValuePropagator.this.root.indexOf(def));
				graph.excavate(def);
				return e;
			} else {
				return v;
			}
		}

		@Override
		public Statement visit(Statement s) {
			if(s instanceof VarExpression) {
				Set<CopyVarStatement> defs = reachingDefs.get(s.toString());
				VarExpression v = (VarExpression) s;
				
				if (defs.size() == 1) {
					CopyVarStatement def = defs.iterator().next();
					Expression rhs = def.getExpression();
					if(rhs instanceof ConstantExpression) {
						return rhs.copy();
					} else if(rhs instanceof VarExpression) {
						// s == def.var == v
						return processVar(v, (VarExpression) rhs);
					} else if(rhs instanceof UninitialisedObjectExpression) {
						// var1 = new java.lang.Object
						// ... 
						// use(var1);
						//  note, that this use of var1 will presumably be
						//  a call to <init>.
						
						// if there is only 1 use of this var, then we can 
						// propagate the new creation expression, otherwise
						// we can only propagate the var that it is assigned to.
						
						// example:
						//   var1 = new java.lang.Object
						//   use(var1)
						//   use(var1)
						// we obviously can't copy the new obj and put it into
						// both uses, we can only propagate the variable (here
						// propagating the variable would be useless).
						// 
						//   var1 = new java.lang.Object
						//   use(var1)
						// this, however, can be transformed to:
						//   use(new java.lang.Object);
						// return processNewObject(def, v, (UninitialisedObjectExpression) rhs);
					}
				}
			}
			return s;
		}
	}
}