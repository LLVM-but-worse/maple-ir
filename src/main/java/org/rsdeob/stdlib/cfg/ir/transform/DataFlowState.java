package org.rsdeob.stdlib.cfg.ir.transform;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BinaryOperator;

import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;

public class DataFlowState {
	public static final DataFlowExpression TOP_EXPR = new DataFlowExpression();
	public static final DataFlowExpression BOTTOM_EXPR = new DataFlowExpression();

	public CopySet in;
	public CopySet out;
	public final Set<CopyVarStatement> gen;
	public final Set<CopyVarStatement> kill;

	public DataFlowState(HashSet<CopyVarStatement> gen, HashSet<CopyVarStatement> kill) {
		in = new CopySet();
		out = new CopySet();
		this.gen = gen;
		this.kill = kill;
	}

	public static class CopySet extends HashMap<String, CopyVarStatement> {
		public CopySet() {
			super();
		}

		public CopySet(CopySet other) {
			super(other);
		}

		// Apply logic table
		private CopySet binaryOper(CopySet other, BinaryOperator<Expression> fn) {
			CopySet result = new CopySet();
			HashSet<CopyVarStatement> copies = new HashSet<>(this.values());
			copies.addAll(other.values());
			for (CopyVarStatement copy : copies) {
				VarExpression var = copy.getVariable();
				String s = var.toString();
				Expression rhs;
				if (this.containsKey(s) && other.containsKey(s))
					rhs = fn.apply(this.get(s).getExpression(), other.get(s).getExpression());
				else
					rhs = TOP_EXPR;
				result.put(s, new CopyVarStatement(var, rhs));
			}
			return result;
		}

		public CopySet join(CopySet b) {
			return binaryOper(b, CopySet::join);
		}

		public CopySet meet(CopySet b) {
			return binaryOper(b, CopySet::meet);
		}

		private static Expression join(Expression a, Expression b) {
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

		private static Expression meet(Expression a, Expression b) {
			if (a == BOTTOM_EXPR)
				return b;
			else if (b == BOTTOM_EXPR)
				return a;
			else if (a == TOP_EXPR || b == TOP_EXPR)
				return TOP_EXPR;
			else if (a.equals(b))
				return a;
			else
				return BOTTOM_EXPR;
		}
	}
}
