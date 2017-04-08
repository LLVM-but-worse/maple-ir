package org.mapleir.deob.intraproc.eval;

import org.mapleir.deob.passes.FieldRSADecryptionPass;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.expr.*;
import org.mapleir.ir.code.expr.ArithmeticExpr.Operator;
import org.mapleir.ir.code.stmt.ConditionalJumpStmt;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStmt;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.stdlib.collections.TaintableSet;
import org.mapleir.stdlib.util.TypeUtils;
import org.objectweb.asm.Type;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.mapleir.ir.code.Opcode.*;
import static org.mapleir.ir.code.expr.ArithmeticExpr.Operator.*;

public class ExpressionEvaluator {
	private final EvaluationFactory factory;
	
	public ExpressionEvaluator(EvaluationFactory factory) {
		this.factory = factory;
	}
	
	public ConstantExpr eval(LocalsPool pool, Expr e) {
		if(e.getOpcode() == CONST_LOAD) {
			return ((ConstantExpr) e).copy();
		} else if(e.getOpcode() == ARITHMETIC) {
			ArithmeticExpr ae = (ArithmeticExpr) e;
			Expr l = ae.getLeft();
			Expr r = ae.getRight();
			
			Expr le = eval(pool, l);
			Expr re = eval(pool, r);
			
			if(le != null && re != null) {
				ConstantExpr lc = (ConstantExpr) le;
				ConstantExpr rc = (ConstantExpr) re;
				
				EvaluationFunctor<Number> b = factory.arithmetic(lc.getType(), rc.getType(), ae.getType(), ae.getOperator());
				return new ConstantExpr(b.eval(lc.getConstant(), rc.getConstant()));
			}
		} else if(e.getOpcode() == NEGATE) {
			NegationExpr neg = (NegationExpr) e;
			Expr e2 = eval(pool, neg.getExpression());
			
			if(e2 != null) {
				ConstantExpr ce = (ConstantExpr) e2;
				EvaluationFunctor<Number> b = factory.negate(e2.getType());
				
				return new ConstantExpr(b.eval(ce.getConstant()));
			}
		} else if(e.getOpcode() == LOCAL_LOAD) {
			VarExpr v = (VarExpr) e;
			Local l = v.getLocal();
			
			AbstractCopyStmt def = pool.defs.get(l);
			Expr rhs = def.getExpression();
			
			if(rhs.getOpcode() == LOCAL_LOAD) {
				VarExpr v2 = (VarExpr) rhs;
				
				// synthetic copies lhs = rhs;
				if(v2.getLocal() == l) {
					return null;
				}
			}
			
			return eval(pool, rhs);
		} else if(e.getOpcode() == CAST) {
			CastExpr cast = (CastExpr) e;
			Expr e2 = eval(pool, cast.getExpression());
			
			if(e2 != null) {
				ConstantExpr ce = (ConstantExpr) e2;
				
				if(!ce.getType().equals(cast.getExpression().getType())) {
					throw new IllegalStateException(ce.getType() + " : " + cast.getExpression().getType());
				}
				Type from = ce.getType();
				Type to = cast.getType();
				
				boolean p1 = TypeUtils.isPrimitive(from);
				boolean p2 = TypeUtils.isPrimitive(to);
				
				if(p1 != p2) {
					throw new IllegalStateException(from + " to " + to);
				}
				
				if(!p1 && !p2) {
					return null;
				}
				
				EvaluationFunctor<Number> b = factory.cast(from, to);
				
				return new ConstantExpr(b.eval(ce.getConstant()), to);
			}
		} else if(e.getOpcode() == COMPARE) {
			ComparisonExpr comp = (ComparisonExpr) e;
			
			Expr l = comp.getLeft();
			Expr r = comp.getRight();
			
			Expr le = eval(pool, l);
			Expr re = eval(pool, r);
			
			if(le != null && re != null) {
				ConstantExpr lc = (ConstantExpr) le;
				ConstantExpr rc = (ConstantExpr) re;
				
				EvaluationFunctor<Number> b = factory.compare(lc.getType(), rc.getType(), comp.getComparisonType());
				
				return new ConstantExpr(b.eval(lc.getConstant(), rc.getConstant()), Type.INT_TYPE);
			}
		}
		
		return null;
	}
	
	public TaintableSet<ConstantExpr> evalPossibleValues(LocalValueResolver resolver, Expr e) {
		return evalPossibleValues0(new HashSet<>(), resolver, e);
	}
	
	private TaintableSet<ConstantExpr> evalPossibleValues0(Set<Local> visited, LocalValueResolver resolver, Expr e) {
		if(e.getOpcode() == CONST_LOAD) {
			TaintableSet<ConstantExpr> set = new TaintableSet<>();
			set.add((ConstantExpr) e);
			return set;
		} /*else if(e.getOpcode() == PHI) {
			PhiExpr phi = (PhiExpr) e;
			
			TaintableSet<ConstantExpr> set = new HashSet<>();
			
			for(Expr pA : phi.getArguments().values()) {
				TaintableSet<ConstantExpr> s = evalPossibleValues(resolver, pA);
				
				if(isValidSet(s)) {
					set.addAll(s);
				}
			}
			
			return set;
		}*/ else if(e.getOpcode() == ARITHMETIC) {
			ArithmeticExpr ae = (ArithmeticExpr) e;
			Expr l = ae.getLeft();
			Expr r = ae.getRight();
			
			TaintableSet<ConstantExpr> le = evalPossibleValues0(visited, resolver, l);
			TaintableSet<ConstantExpr> re = evalPossibleValues0(visited, resolver, r);
			
			if(!le.isUnconst() && !re.isUnconst()) {
				TaintableSet<ConstantExpr> results = new TaintableSet<>();
				
				// TODO cartesian product
				for(ConstantExpr lc : le) {
					for(ConstantExpr rc : re) {
						EvaluationFunctor<Number> b = factory.arithmetic(lc.getType(), rc.getType(), ae.getType(), ae.getOperator());
						results.add(new ConstantExpr(b.eval(lc.getConstant(), rc.getConstant())));
					}
				}
				
				return results;
			}
		} else if(e.getOpcode() == NEGATE) {
			NegationExpr neg = (NegationExpr) e;
			TaintableSet<ConstantExpr> vals = evalPossibleValues0(visited, resolver, neg.getExpression());
			
			if(!vals.isUnconst()) {
				TaintableSet<ConstantExpr> results = new TaintableSet<>();
				
				for(ConstantExpr c : vals) {
					EvaluationFunctor<Number> b = factory.negate(c.getType());
					results.add(new ConstantExpr(b.eval(c.getConstant())));
				}
				
				return results;
			}
		} else if(e.getOpcode() == LOCAL_LOAD) {
			VarExpr v = (VarExpr) e;
			Local l = v.getLocal();
			
			visited.add(l);
			
			Set<Expr> defExprs = resolver.getValues(l);

			if(defExprs != null && !defExprs.isEmpty()) {
				TaintableSet<ConstantExpr> vals = new TaintableSet<>();
				
				for(Expr defE : defExprs) {
					if(defE.getOpcode() == LOCAL_LOAD) {
						VarExpr v2 = (VarExpr) defE;
						
						/*// synthetic copies lhs = rhs;
						if(v2.getLocal() == l) {
							continue;
						}*/
						
						Local l2 = v2.getLocal();
						
						if(!visited.add(l2)) {
							continue;
						}
					}
					
					TaintableSet<ConstantExpr> set2 = evalPossibleValues0(visited, resolver, defE);
					vals.union(set2);
				}
				
				return vals;
			}
		} else if(e.getOpcode() == CAST) {
			CastExpr cast = (CastExpr) e;
			TaintableSet<ConstantExpr> set = evalPossibleValues0(visited, resolver, cast.getExpression());
			
			if(!set.isUnconst()) {
				TaintableSet<ConstantExpr> results = new TaintableSet<>();
				
				for(ConstantExpr ce : set) {
					// TODO: czech out::
					// can get expressions like (double)({lvar7_1 * 4})
					// where {lvar7_1 * 4} has type INT but the real
					// eval consts are all bytes or shorts etc
					
					/*if(!ce.getType().equals(cast.getExpression().getType())) {
						System.err.printf("want to cast %s%n", cast);
						System.err.printf(" in: %s, death: %s%n", set, ce);
						throw new IllegalStateException(ce.getType() + " : " + cast.getExpression().getType());
					}*/
					Type from = ce.getType();
					Type to = cast.getType();
					
					boolean p1 = TypeUtils.isPrimitive(from);
					boolean p2 = TypeUtils.isPrimitive(to);
					
					if(p1 != p2) {
						throw new IllegalStateException(from + " to " + to);
					}
					
					if(!p1 && !p2) {
						return new TaintableSet<>();
					}
					
					EvaluationFunctor<Number> b = factory.cast(from, to);
					
					results.add(new ConstantExpr(b.eval(ce.getConstant())));
				}
				
				return results;
			}
		} else if(e.getOpcode() == COMPARE) {
//			throw new UnsupportedOperationException("todo lmao");
//			ComparisonExpr comp = (ComparisonExpr) e;

//			Expr l = comp.getLeft();
//			Expr r = comp.getRight();
//
//			Expr le = eval(pool, l);
//			Expr re = eval(pool, r);
//
//			if(le != null && re != null) {
//				ConstantExpr lc = (ConstantExpr) le;
//				ConstantExpr rc = (ConstantExpr) re;
//
//				Bridge b = getComparisonBridge(lc.getType(), rc.getType(), comp.getComparisonType());
//
//				System.out.println(b.method);
//				System.out.println(comp + " -> " + b.eval(lc.getConstant(), rc.getConstant()));
//				ConstantExpr cr = new ConstantExpr((int)b.eval(lc.getConstant(), rc.getConstant()));
//				return cr;
//			}
		}
		
		return new TaintableSet<>();
	}
	
	public Boolean evaluatePrimitiveConditional(ConditionalJumpStmt cond, TaintableSet<ConstantExpr> leftSet, TaintableSet<ConstantExpr> rightSet) {
		Boolean val = null;
		
		for(ConstantExpr lc : leftSet) {
			for(ConstantExpr rc : rightSet) {
				if(TypeUtils.isPrimitive(lc.getType()) && TypeUtils.isPrimitive(rc.getType())) {
					EvaluationFunctor<Boolean> bridge = factory.branch(lc.getType(), rc.getType(), cond.getComparisonType());
					/*System.out.println("eval: " + bridge.method + " " + lc.getConstant().getClass() + " " + rc.getConstant().getClass());
					System.out.println("   actual: " + lc.getType() + ", " +  rc.getType());
					System.out.println("      " + lc.getConstant() +"  " + rc.getConstant());*/
					
					boolean branchVal = bridge.eval(lc.getConstant(), rc.getConstant());
					
					if(val != null) {
						if(val != branchVal) {
							return null;
						}
					} else {
						val = branchVal;
					}
				} else {
					/*System.err.println("something::");
					System.err.println("  " + cond);
					System.err.println("  leftset: " + leftSet);
					System.err.println("  rightSet: " + rightSet);|
					return;*/
					throw new UnsupportedOperationException();
				}
			}
		}

		return val;
	}
	
	private Expr simplifyMultiplication(LocalsPool pool, ArithmeticExpr e) {
		if (e.getOperator() != Operator.MUL)
			throw new IllegalArgumentException("Only works on multiplication exprs");
		
		Expr r = e.getRight();
		
		Expr re = eval(pool, r);
		
		if(re instanceof ConstantExpr) {
			ConstantExpr ce =(ConstantExpr) re;
			
			Object o = ce.getConstant();
			
			if(o instanceof Integer || o instanceof Long) {
				if(FieldRSADecryptionPass.__eq((Number) o, 1, o instanceof Long)) {
					return e.getLeft().copy();
				} else if(FieldRSADecryptionPass.__eq((Number) o, 0, o instanceof Long)) {
					return new ConstantExpr(0, ce.getType());
				}
			}
		}
		
		return null;
	}
	
	private ArithmeticExpr simplifyAddition(LocalsPool pool, ArithmeticExpr ae) {
		if (ae.getOperator() != Operator.ADD)
			throw new IllegalArgumentException("Only works on addition exprs");
		
		Expr rhs = ae.getRight();
		// a + -(b) => a - b
		if (rhs.getOpcode() == NEGATE) {
			ConstantExpr r = eval(pool, ((NegationExpr) rhs).getExpression());
			return new ArithmeticExpr(r, ae.getLeft().copy(), SUB);
		}
		
		// a + -b => a - b
		if (rhs.getOpcode() == CONST_LOAD) {
			if (new BigDecimal(((ConstantExpr) rhs).getConstant().toString()).signum() < 0) {
				ConstantExpr negatedR = eval(pool, new NegationExpr(rhs.copy()));
				if (negatedR != null) {
					return new ArithmeticExpr(negatedR, ae.getLeft().copy(), SUB);
				}
			}
		}
		return null;
	}
	
	private ArithmeticExpr reassociate(LocalsPool pool, ArithmeticExpr ae) {
		ArithmeticExpr leftAe = (ArithmeticExpr) ae.getLeft();
		Operator operatorA = leftAe.getOperator();
		Operator operatorB = ae.getOperator();
		
		Expr r1 = eval(pool, leftAe.getRight());
		Expr r2 = eval(pool, ae.getRight());
		if (r1 != null && r2 != null) {
			ConstantExpr cr1 = (ConstantExpr) r1;
			ConstantExpr cr2 = (ConstantExpr) r2;
			
			int sign = 0;
			if ((operatorA == MUL && operatorB == MUL)) {
				sign = 1;
			} else if (operatorA == ADD && (operatorB == ADD || operatorB == SUB)) {
				sign = 1; // what about overflow?? integers mod 2^32 forms a group over addition...should be ok?
			} else if (operatorA == SUB && (operatorB == ADD || operatorB == SUB)) {
				sign = -1;
			}
			if (sign != 0) {
				ConstantExpr cr1r2 = eval(pool, new ArithmeticExpr(sign > 0 ? cr2 : new NegationExpr(cr2), cr1, operatorB));
				Object associated = cr1r2.getConstant();
				return new ArithmeticExpr(new ConstantExpr(associated, cr1r2.getType()), leftAe.getLeft().copy(), operatorA);
			}
		}
		return null;
	}
	
	public Expr simplifyArithmetic(LocalsPool pool, ArithmeticExpr ae) {
		Expr e2 = null;
		if (ae.getOperator() == MUL) { // try to simplify multiplication
			e2 = simplifyMultiplication(pool, ae);
		}
		if (e2 == null && ae.getOperator() == ADD) { // try to simplify addition
			e2 = simplifyAddition(pool, ae);
		}
		if (e2 == null && ae.getLeft().getOpcode() == ARITHMETIC) { // try to apply associative properties
			e2 = reassociate(pool, ae);
		}
		return e2;
	}
}