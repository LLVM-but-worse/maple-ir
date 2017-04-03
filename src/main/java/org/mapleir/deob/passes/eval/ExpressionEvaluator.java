package org.mapleir.deob.passes.eval;

import org.mapleir.deob.passes.FieldRSADecryptionPass;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.expr.*;
import org.mapleir.ir.code.expr.ArithmeticExpr.Operator;
import org.mapleir.ir.code.stmt.ConditionalJumpStmt;
import org.mapleir.ir.code.stmt.copy.AbstractCopyStmt;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.LocalsPool;
import org.mapleir.stdlib.util.TypeUtils;
import org.objectweb.asm.Type;

import java.util.HashSet;
import java.util.Set;

import static org.mapleir.ir.code.Opcode.*;
import static org.mapleir.ir.code.expr.ArithmeticExpr.Operator.*;

public class ExpressionEvaluator {
	BridgeFactory bridgeFactory;
	
	public ExpressionEvaluator() {
		bridgeFactory = new BridgeFactory();
	}
	
	private static boolean isValidSet(Set<?> set) {
		return set != null && set.size() > 0;
	}
	
	private static <T> Set<T> returnCleanSet(Set<T> set) {
		if(set != null && set.size() > 0) {
			return set;
		} else {
			return null;
		}
	}
	
	public Expr eval(LocalsPool pool, Expr e) {
		if(e.getOpcode() == CONST_LOAD) {
			return e.copy();
		} else if(e.getOpcode() == ARITHMETIC) {
			ArithmeticExpr ae = (ArithmeticExpr) e;
			Expr l = ae.getLeft();
			Expr r = ae.getRight();
			
			Expr le = eval(pool, l);
			Expr re = eval(pool, r);
			
			if(le != null && re != null) {
				ConstantExpr lc = (ConstantExpr) le;
				ConstantExpr rc = (ConstantExpr) re;
				
				Bridge b = bridgeFactory.getArithmeticBridge(lc.getType(), rc.getType(), ae.getType(), ae.getOperator());
				
				return new ConstantExpr(b.eval(lc.getConstant(), rc.getConstant()), ae.getType());
			}
		} else if(e.getOpcode() == NEGATE) {
			NegationExpr neg = (NegationExpr) e;
			Expr e2 = eval(pool, neg.getExpression());
			
			if(e2 != null) {
				ConstantExpr ce = (ConstantExpr) e2;
				Bridge b = bridgeFactory.getNegationBridge(e2.getType());
				
				return new ConstantExpr(b.eval(ce.getConstant()), ce.getType());
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
				
				Bridge b = bridgeFactory.getCastBridge(from, to);
				
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
				
				Bridge b = bridgeFactory.getComparisonBridge(lc.getType(), rc.getType(), comp.getComparisonType());
				
				return new ConstantExpr(b.eval(lc.getConstant(), rc.getConstant()), Type.INT_TYPE);
			}
		}
		
		return null;
	}
	
	public Expr simplifyMultiplication(LocalsPool pool, ArithmeticExpr e) {
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
	
	public ArithmeticExpr reassociate(LocalsPool pool, ArithmeticExpr ae) {
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
				Expr cr1r2 = eval(pool, new ArithmeticExpr(cr1, sign > 0 ? cr2 : new NegationExpr(cr2), operatorB));
				Object associated = ((ConstantExpr) cr1r2).getConstant();
				return new ArithmeticExpr(new ConstantExpr(associated, r1.getType()), leftAe.getLeft().copy(), operatorA);
			}
		}
		return null;
	}
	
	public Expr simplifyArithmetic(LocalsPool pool, ArithmeticExpr ae) {
		Expr e2 = null;
		if (ae.getOperator() == MUL) { // try to simplify multiplication
			e2 = simplifyMultiplication(pool, ae);
		}
		if (e2 == null && ae.getLeft().getOpcode() == ARITHMETIC) { // try to apply associative properties
			e2 = reassociate(pool, ae);
		}
		return e2;
	}
	
	public Set<ConstantExpr> evalPossibleValues(LocalValueResolver resolver, Expr e) {
		if(e.getOpcode() == CONST_LOAD) {
			Set<ConstantExpr> set = new HashSet<>();
			set.add((ConstantExpr) e);
			return set;
		} else if(e.getOpcode() == ARITHMETIC) {
			ArithmeticExpr ae = (ArithmeticExpr) e;
			Expr l = ae.getLeft();
			Expr r = ae.getRight();
			
			Set<ConstantExpr> le = evalPossibleValues(resolver, l);
			Set<ConstantExpr> re = evalPossibleValues(resolver, r);
			
			if(isValidSet(le) && isValidSet(re)) {
				Set<ConstantExpr> results = new HashSet<>();
				
				for(ConstantExpr lc : le) {
					for(ConstantExpr rc : re) {
						Bridge b = bridgeFactory.getArithmeticBridge(lc.getType(), rc.getType(), ae.getType(), ae.getOperator());
						results.add(new ConstantExpr(b.eval(lc.getConstant(), rc.getConstant()), ae.getType()));
					}
				}
				
				return returnCleanSet(results);
			}
		} else if(e.getOpcode() == NEGATE) {
			NegationExpr neg = (NegationExpr) e;
			Set<ConstantExpr> vals = evalPossibleValues(resolver, neg.getExpression());
			
			if(isValidSet(vals)) {
				Set<ConstantExpr> results = new HashSet<>();
				
				for(ConstantExpr c : vals) {
					Bridge b = bridgeFactory.getNegationBridge(c.getType());
					results.add(new ConstantExpr(b.eval(c.getConstant()), c.getType()));
				}
				
				return returnCleanSet(results);
			}
		} else if(e.getOpcode() == LOCAL_LOAD) {
			VarExpr v = (VarExpr) e;
			Local l = v.getLocal();
			
			Set<Expr> defExprs = resolver.getValues(l);

			if(isValidSet(defExprs)) {
				Set<ConstantExpr> vals = new HashSet<>();
				
				for(Expr defE : defExprs) {
					if(defE.getOpcode() == LOCAL_LOAD) {
						VarExpr v2 = (VarExpr) defE;
						
						// synthetic copies lhs = rhs;
						if(v2.getLocal() == l) {
							continue;
						}
					}
					
					Set<ConstantExpr> set2 = evalPossibleValues(resolver, defE);
					if(isValidSet(set2)) {
						vals.addAll(set2);
					}
				}
				
				return returnCleanSet(vals);
			}
		} else if(e.getOpcode() == CAST) {
			CastExpr cast = (CastExpr) e;
			Set<ConstantExpr> set = evalPossibleValues(resolver, cast.getExpression());
			
			if(isValidSet(set)) {
				Set<ConstantExpr> results = new HashSet<>();
				
				for(ConstantExpr ce : set) {
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
					
					Bridge b = bridgeFactory.getCastBridge(from, to);
					
					results.add(new ConstantExpr(b.eval(ce.getConstant()), to));
				}
				
				return returnCleanSet(results);
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
		
		return null;
	}
	
	private Boolean evaluatePrimitiveConditional(ConditionalJumpStmt cond, Set<ConstantExpr> leftSet, Set<ConstantExpr> rightSet) {
		Boolean val = null;
		
		for(ConstantExpr lc : leftSet) {
			for(ConstantExpr rc : rightSet) {
				if(TypeUtils.isPrimitive(lc.getType()) && TypeUtils.isPrimitive(rc.getType())) {
					Bridge bridge = bridgeFactory.getConditionalEvalBridge(lc.getType(), rc.getType(), cond.getComparisonType());
					/*System.out.println("eval: " + bridge.method + " " + lc.getConstant().getClass() + " " + rc.getConstant().getClass());
					System.out.println("   actual: " + lc.getType() + ", " +  rc.getType());
					System.out.println("      " + lc.getConstant() +"  " + rc.getConstant());*/
					
					boolean branchVal = (boolean) bridge.eval(lc.getConstant(), rc.getConstant());
					
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
	
	public Boolean evaluateConditional(IPConstAnalysisVisitor vis, ControlFlowGraph cfg, ConditionalJumpStmt cond) {
		Expr l = cond.getLeft();
		Expr r = cond.getRight();
		
		if (!TypeUtils.isPrimitive(l.getType()) || !TypeUtils.isPrimitive(r.getType())) {
			if(l instanceof ConstantExpr && r instanceof ConstantExpr && !TypeUtils.isPrimitive(l.getType()) && !TypeUtils.isPrimitive(r.getType())) {
				ConstantExpr left = (ConstantExpr) l;
				ConstantExpr right = (ConstantExpr) r;
				if (left.getConstant() == null && right.getConstant() == null) {
					return cond.getComparisonType() == ConditionalJumpStmt.ComparisonType.EQ;
				}
				if (cond.getComparisonType() == ConditionalJumpStmt.ComparisonType.EQ) {
					if ((left.getConstant() == null) != (right.getConstant() == null)) {
						return false;
					}
				}
				return null;
			}
			return null;
		}
		
		LocalValueResolver resolver;
		
		LocalsPool pool = cfg.getLocals();
		if(vis != null) {
			// FIXME: use
			resolver = new LocalValueResolver.SemiConstantLocalValueResolver(cfg.getMethod(), pool, vis);
		} else {
			resolver = new LocalValueResolver.PooledLocalValueResolver(pool);
		}
		
		Set<ConstantExpr> lSet = evalPossibleValues(resolver, l);
		Set<ConstantExpr> rSet = evalPossibleValues(resolver, r);
		
		if(isValidSet(lSet) && isValidSet(rSet)) {
			Boolean result = evaluatePrimitiveConditional(cond, lSet, rSet);
			if (result != null) {
				return result;
			}
		}
		return null;
	}
}
