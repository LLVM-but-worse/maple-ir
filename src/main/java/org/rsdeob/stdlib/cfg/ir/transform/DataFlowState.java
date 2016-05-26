package org.rsdeob.stdlib.cfg.ir.transform;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.function.BinaryOperator;

public class DataFlowState {
	public static final DataFlowExpression TOP_EXPR = new DataFlowExpression();
	public static final DataFlowExpression BOTTOM_EXPR = new DataFlowExpression();

	public CopySet in;
	public CopySet out;
	public final HashSet<CopyVarStatement> gen;
	public final HashSet<CopyVarStatement> kill;

	public DataFlowState(HashSet<CopyVarStatement> gen, HashSet<CopyVarStatement> kill) {
		in = new CopySet();
		out = new CopySet();
		this.gen = gen;
		this.kill = kill;
	}

	private static class DataFlowExpression extends Expression {
		@Override
		public String toString() {
			return this == TOP_EXPR ? "T" : "_|_";
		}

		@Override
		public void onChildUpdated(int ptr) {

		}

		@Override
		public void toString(TabbedStringWriter printer) {

		}

		@Override
		public void toCode(MethodVisitor visitor) {
			throw new UnsupportedOperationException("TopExpression is for data flow use only");
		}

		@Override
		public boolean canChangeFlow() {
			return false;
		}

		@Override
		public boolean canChangeLogic() {
			return false;
		}

		@Override
		public boolean isAffectedBy(Statement stmt) {
			return false;
		}

		@Override
		public Expression copy() {
			throw new UnsupportedOperationException("Do not copy TopExpression; instantiate a new one instead");
		}

		@Override
		public Type getType() {
			throw new UnsupportedOperationException("TopExpression has no type");
		}
	}

	@SuppressWarnings("Duplicates")
	public static class CopySet extends HashMap<VarExpression, CopyVarStatement> {
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
				Expression rhs;
				if (this.containsKey(var) && other.containsKey(var))
					rhs = fn.apply(this.get(var).getExpression(), other.get(var).getExpression());
				else
					rhs = TOP_EXPR;
				result.put(var, new CopyVarStatement(var, rhs));
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
