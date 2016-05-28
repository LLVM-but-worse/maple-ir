package org.rsdeob.stdlib.cfg.ir.transform;

import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.ir.expr.ConstantExpression;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.function.BinaryOperator;

import static org.rsdeob.stdlib.cfg.ir.transform.DataFlowExpression.BOTTOM_EXPR;
import static org.rsdeob.stdlib.cfg.ir.transform.DataFlowExpression.TOP_EXPR;
import static org.rsdeob.stdlib.cfg.ir.transform.DataFlowState.CopySet.AllVarsExpression.VAR_ALL;

public class DataFlowState {
	public CopySet in;
	public CopySet out;

	public DataFlowState() {
		in = new CopySet();
		out = new CopySet();
	}

	public static class CopySet extends HashMap<VarExpression, CopyVarStatement> {
		public CopySet() {
			super();
		}

		public CopySet(CopySet other) {
			super(other);
		}

		public void setVar(CopyVarStatement copy) {
			put(copy.getVariable(), copy);
		}

		@Override
		public boolean containsKey(Object key) {
			if (super.containsKey(VAR_ALL))
				return key instanceof VarExpression;
			return super.containsKey(key);
		}

		@Override
		public CopyVarStatement get(Object key) {
			if (super.containsKey(key))
				return super.get(key);
			if (super.containsKey(VAR_ALL))
				return super.get(VAR_ALL);
			return null;
		}

		// Apply logic table
		private CopySet binaryOper(CopySet other, BinaryOperator<Expression> fn) {
			CopySet result = new CopySet();
			HashSet<CopyVarStatement> copies = new HashSet<>(this.values());
			copies.addAll(other.values());
			for (CopyVarStatement copy : copies) {
				VarExpression var = copy.getVariable();
				Expression rhs;
				if (this.containsKey(var) && other.containsKey(var))
					rhs = fn.apply(this.get(var).getExpression(), other.get(var).getExpression());
				else
					rhs = TOP_EXPR;
				result.put(var, new CopyVarStatement(var, rhs));
			}
			return result;
		}

		public CopySet meet(CopySet b) {
			return binaryOper(b, CopySet::meet);
		}

		private static Expression meet(Expression a, Expression b) {
			if (a == TOP_EXPR)
				return b;
			else if (b == TOP_EXPR)
				return a;
			else if (a == BOTTOM_EXPR || b == BOTTOM_EXPR)
				return BOTTOM_EXPR;
			else if (a.equals(b))
				return a;
			else
				return BOTTOM_EXPR;
		}

		public boolean trans(CopyVarStatement stmt) {
			VarExpression var = stmt.getVariable();
			Expression rhs = stmt.getExpression();
			if (!containsKey(var))
				return false;
			Expression expr = get(var).getExpression();
			Expression newExpr;
			if (expr == TOP_EXPR)
				newExpr = TOP_EXPR;
			else if (!(rhs instanceof ConstantExpression)) { // todo: we need to find a way to evaluate complex exprs to determine whether they are constant.
				if (rhs instanceof VarExpression) {
					if (containsKey(rhs) && get(rhs).getExpression() instanceof ConstantExpression) {
						newExpr = get(rhs).getExpression();
					} else {
						newExpr = BOTTOM_EXPR;
					}
				} else {
					newExpr = BOTTOM_EXPR;
				}
			}
			else
				newExpr = rhs;
			if (expr.equals(newExpr))
				return false;
			put(var, new CopyVarStatement(var, newExpr));
			return true;
		}

		public static class AllVarsExpression extends VarExpression {
			public static AllVarsExpression VAR_ALL = new AllVarsExpression();

			private AllVarsExpression() {
				super(-1, Type.VOID_TYPE, true);
			}

			@Override
			public boolean equals(Object o) {
				return o instanceof VarExpression;
			}

			@Override
			public void toString(TabbedStringWriter printer) {
				printer.print("var*");
			}
		}
	}
}
