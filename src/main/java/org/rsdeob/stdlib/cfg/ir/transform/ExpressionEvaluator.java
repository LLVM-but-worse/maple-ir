package org.rsdeob.stdlib.cfg.ir.transform;

import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.ir.StatementVisitor;
import org.rsdeob.stdlib.cfg.ir.expr.ArithmeticExpression;
import org.rsdeob.stdlib.cfg.ir.expr.ConstantExpression;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.ir.transform.DataFlowState.CopySet;
import org.rsdeob.stdlib.cfg.util.TypeUtils;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.objectweb.asm.Type.*;

public class ExpressionEvaluator {
	public static Expression evaluate(Expression in, CopySet vars) {
		if (in instanceof VarExpression && vars.containsKey(in))
			in = vars.get(in).getExpression();
		Expression result = in.copy();

		final AtomicBoolean changed = new AtomicBoolean(true); // seriously???
		while (changed.get()) {
			changed.set(false);
			new StatementVisitor(result) {
				@Override
				public Statement visit(Statement stmt) {
					if (stmt instanceof Expression) {
						Expression expr = (Expression) stmt;
						if (expr instanceof VarExpression && vars.containsKey(expr)) {
							changed.set(true);
							return vars.get(expr).getExpression();
						} else if (isConstant(expr)) {
							return evaluateConstant(expr);
						} else {
							return expr;
						}
					} else {
						return stmt;
					}
				}
			}.visit();
		}
		return result;
	}

	public static boolean isConstant(Expression expr) {
		if (expr instanceof VarExpression || expr instanceof DataFlowExpression)
			return false;

		final AtomicBoolean constant = new AtomicBoolean(true);
		new StatementVisitor(expr) {
			@Override
			public Statement visit(Statement stmt) {
				if (stmt instanceof VarExpression || stmt instanceof DataFlowExpression)
					constant.set(false);
				return stmt;
			}
		}.visit();
		return constant.get();
	}

	// expr must be evaluatable to constant
	private static ConstantExpression evaluateConstant(Expression expr) {
		if (expr instanceof ConstantExpression)
			return (ConstantExpression) expr;
		// subcalls are safe because the entire expr is constant
		if (expr instanceof ArithmeticExpression)
			return evaluateArithmetic((ArithmeticExpression) expr);
		throw new NotImplementedException();
	}

	// expr must be evaluatable to constant
	private static ConstantExpression evaluateArithmetic(ArithmeticExpression arith) {
		ConstantExpression lhs = evaluateConstant(arith.getLeft());
		ConstantExpression rhs = evaluateConstant(arith.getRight());

		Type lhsType = lhs.getType();
		Type rhsType = rhs.getType();
		if (!TypeUtils.isPrimitive(lhsType) || !TypeUtils.isPrimitive(rhsType))
			throw new IllegalArgumentException("Arithmetic expression of non primitive types");

		ArithmeticExpression.Operator operator = arith.getOperator();
		Object lhsValue = lhs.getConstant();
		Object rhsValue = rhs.getConstant();

		Object result = null;
		if (operator.ordinal() >= ArithmeticExpression.Operator.SHL.ordinal()) {
			if ((lhsType == FLOAT_TYPE || lhsType == DOUBLE_TYPE) && (rhsType == FLOAT_TYPE || rhsType == DOUBLE_TYPE))
				throw new IllegalArgumentException("Illegal types for bitwise operation");

			switch (operator) {
				case SHL:
					if (lhsType == CHAR_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (char) lhsValue << (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (char) lhsValue << (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (char) lhsValue << (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (char) lhsValue << (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (char) lhsValue << (long) rhsValue;
					}
					if (lhsType == BYTE_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (byte) lhsValue << (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (byte) lhsValue << (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (byte) lhsValue << (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (byte) lhsValue << (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (byte) lhsValue << (long) rhsValue;
					}
					if (lhsType == SHORT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (short) lhsValue << (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (short) lhsValue << (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (short) lhsValue << (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (short) lhsValue << (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (short) lhsValue << (long) rhsValue;
					}
					if (lhsType == INT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (int) lhsValue << (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (int) lhsValue << (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (int) lhsValue << (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (int) lhsValue << (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (int) lhsValue << (long) rhsValue;
					}
					if (lhsType == LONG_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (long) lhsValue << (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (long) lhsValue << (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (long) lhsValue << (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (long) lhsValue << (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (long) lhsValue << (long) rhsValue;
					}
					break;
				case SHR:
					if (lhsType == CHAR_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (char) lhsValue >> (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (char) lhsValue >> (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (char) lhsValue >> (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (char) lhsValue >> (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (char) lhsValue >> (long) rhsValue;
					}
					if (lhsType == BYTE_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (byte) lhsValue >> (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (byte) lhsValue >> (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (byte) lhsValue >> (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (byte) lhsValue >> (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (byte) lhsValue >> (long) rhsValue;
					}
					if (lhsType == SHORT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (short) lhsValue >> (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (short) lhsValue >> (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (short) lhsValue >> (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (short) lhsValue >> (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (short) lhsValue >> (long) rhsValue;
					}
					if (lhsType == INT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (int) lhsValue >> (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (int) lhsValue >> (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (int) lhsValue >> (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (int) lhsValue >> (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (int) lhsValue >> (long) rhsValue;
					}
					if (lhsType == LONG_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (long) lhsValue >> (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (long) lhsValue >> (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (long) lhsValue >> (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (long) lhsValue >> (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (long) lhsValue >> (long) rhsValue;
					}
					break;
				case USHR:
					if (lhsType == CHAR_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (char) lhsValue >>> (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (char) lhsValue >>> (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (char) lhsValue >>> (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (char) lhsValue >>> (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (char) lhsValue >>> (long) rhsValue;
					}
					if (lhsType == BYTE_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (byte) lhsValue >>> (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (byte) lhsValue >>> (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (byte) lhsValue >>> (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (byte) lhsValue >>> (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (byte) lhsValue >>> (long) rhsValue;
					}
					if (lhsType == SHORT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (short) lhsValue >>> (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (short) lhsValue >>> (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (short) lhsValue >>> (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (short) lhsValue >>> (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (short) lhsValue >>> (long) rhsValue;
					}
					if (lhsType == INT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (int) lhsValue >>> (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (int) lhsValue >>> (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (int) lhsValue >>> (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (int) lhsValue >>> (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (int) lhsValue >>> (long) rhsValue;
					}
					if (lhsType == LONG_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (long) lhsValue >>> (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (long) lhsValue >>> (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (long) lhsValue >>> (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (long) lhsValue >>> (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (long) lhsValue >>> (long) rhsValue;
					}
					break;
				case OR:
					if (lhsType == BOOLEAN_TYPE) {
						if (rhsType == BOOLEAN_TYPE)
							result = (boolean) lhsValue | (boolean) rhsValue;
					}
					if (lhsType == CHAR_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (char) lhsValue | (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (char) lhsValue | (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (char) lhsValue | (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (char) lhsValue | (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (char) lhsValue | (long) rhsValue;
					}
					if (lhsType == BYTE_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (byte) lhsValue | (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (byte) lhsValue | (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (byte) lhsValue | (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (byte) lhsValue | (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (byte) lhsValue | (long) rhsValue;
					}
					if (lhsType == SHORT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (short) lhsValue | (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (short) lhsValue | (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (short) lhsValue | (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (short) lhsValue | (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (short) lhsValue | (long) rhsValue;
					}
					if (lhsType == INT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (int) lhsValue | (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (int) lhsValue | (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (int) lhsValue | (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (int) lhsValue | (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (int) lhsValue | (long) rhsValue;
					}
					if (lhsType == LONG_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (long) lhsValue | (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (long) lhsValue | (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (long) lhsValue | (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (long) lhsValue | (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (long) lhsValue | (long) rhsValue;
					}
					break;
				case AND:
					if (lhsType == BOOLEAN_TYPE) {
						if (rhsType == BOOLEAN_TYPE)
							result = (boolean) lhsValue & (boolean) rhsValue;
					}
					if (lhsType == CHAR_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (char) lhsValue & (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (char) lhsValue & (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (char) lhsValue & (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (char) lhsValue & (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (char) lhsValue & (long) rhsValue;
					}
					if (lhsType == BYTE_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (byte) lhsValue & (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (byte) lhsValue & (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (byte) lhsValue & (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (byte) lhsValue & (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (byte) lhsValue & (long) rhsValue;
					}
					if (lhsType == SHORT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (short) lhsValue & (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (short) lhsValue & (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (short) lhsValue & (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (short) lhsValue & (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (short) lhsValue & (long) rhsValue;
					}
					if (lhsType == INT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (int) lhsValue & (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (int) lhsValue & (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (int) lhsValue & (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (int) lhsValue & (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (int) lhsValue & (long) rhsValue;
					}
					if (lhsType == LONG_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (long) lhsValue & (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (long) lhsValue & (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (long) lhsValue & (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (long) lhsValue & (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (long) lhsValue & (long) rhsValue;
					}
					break;
				case XOR:
					if (lhsType == BOOLEAN_TYPE) {
						if (rhsType == BOOLEAN_TYPE)
							result = (boolean) lhsValue ^ (boolean) rhsValue;
					}
					if (lhsType == CHAR_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (char) lhsValue ^ (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (char) lhsValue ^ (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (char) lhsValue ^ (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (char) lhsValue ^ (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (char) lhsValue ^ (long) rhsValue;
					}
					if (lhsType == BYTE_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (byte) lhsValue ^ (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (byte) lhsValue ^ (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (byte) lhsValue ^ (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (byte) lhsValue ^ (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (byte) lhsValue ^ (long) rhsValue;
					}
					if (lhsType == SHORT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (short) lhsValue ^ (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (short) lhsValue ^ (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (short) lhsValue ^ (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (short) lhsValue ^ (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (short) lhsValue ^ (long) rhsValue;
					}
					if (lhsType == INT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (int) lhsValue ^ (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (int) lhsValue ^ (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (int) lhsValue ^ (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (int) lhsValue ^ (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (int) lhsValue ^ (long) rhsValue;
					}
					if (lhsType == LONG_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (long) lhsValue ^ (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (long) lhsValue ^ (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (long) lhsValue ^ (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (long) lhsValue ^ (int) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (long) lhsValue ^ (long) rhsValue;
					}
					break;
				default:
					throw new IllegalArgumentException("Invalid bitwise operator " + operator.name());
			}
		} else {
			switch (operator) {
				case ADD:
					if (lhsType == CHAR_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (char) lhsValue + (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (char) lhsValue + (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (char) lhsValue + (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (char) lhsValue + (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (char) lhsValue + (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (char) lhsValue + (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (char) lhsValue + (double) rhsValue;
					}
					if (lhsType == BYTE_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (byte) lhsValue + (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (byte) lhsValue + (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (byte) lhsValue + (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (byte) lhsValue + (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (byte) lhsValue + (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (byte) lhsValue + (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (byte) lhsValue + (double) rhsValue;
					}
					if (lhsType == SHORT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (short) lhsValue + (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (short) lhsValue + (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (short) lhsValue + (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (short) lhsValue + (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (short) lhsValue + (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (short) lhsValue + (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (short) lhsValue + (double) rhsValue;
					}
					if (lhsType == INT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (int) lhsValue + (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (int) lhsValue + (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (int) lhsValue + (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (int) lhsValue + (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (int) lhsValue + (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (int) lhsValue + (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (int) lhsValue + (double) rhsValue;
					}
					if (lhsType == FLOAT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (float) lhsValue + (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (float) lhsValue + (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (float) lhsValue + (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (float) lhsValue + (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (float) lhsValue + (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (float) lhsValue + (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (float) lhsValue + (double) rhsValue;
					}
					if (lhsType == LONG_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (long) lhsValue + (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (long) lhsValue + (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (long) lhsValue + (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (long) lhsValue + (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (long) lhsValue + (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (long) lhsValue + (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (long) lhsValue + (double) rhsValue;
					}
					if (lhsType == DOUBLE_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (double) lhsValue + (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (double) lhsValue + (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (double) lhsValue + (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (double) lhsValue + (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (double) lhsValue + (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (double) lhsValue + (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (double) lhsValue + (double) rhsValue;
					}
					break;
				case SUB:
					if (lhsType == CHAR_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (char) lhsValue - (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (char) lhsValue - (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (char) lhsValue - (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (char) lhsValue - (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (char) lhsValue - (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (char) lhsValue - (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (char) lhsValue - (double) rhsValue;
					}
					if (lhsType == BYTE_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (byte) lhsValue - (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (byte) lhsValue - (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (byte) lhsValue - (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (byte) lhsValue - (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (byte) lhsValue - (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (byte) lhsValue - (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (byte) lhsValue - (double) rhsValue;
					}
					if (lhsType == SHORT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (short) lhsValue - (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (short) lhsValue - (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (short) lhsValue - (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (short) lhsValue - (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (short) lhsValue - (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (short) lhsValue - (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (short) lhsValue - (double) rhsValue;
					}
					if (lhsType == INT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (int) lhsValue - (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (int) lhsValue - (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (int) lhsValue - (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (int) lhsValue - (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (int) lhsValue - (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (int) lhsValue - (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (int) lhsValue - (double) rhsValue;
					}
					if (lhsType == FLOAT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (float) lhsValue - (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (float) lhsValue - (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (float) lhsValue - (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (float) lhsValue - (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (float) lhsValue - (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (float) lhsValue - (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (float) lhsValue - (double) rhsValue;
					}
					if (lhsType == LONG_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (long) lhsValue - (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (long) lhsValue - (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (long) lhsValue - (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (long) lhsValue - (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (long) lhsValue - (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (long) lhsValue - (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (long) lhsValue - (double) rhsValue;
					}
					if (lhsType == DOUBLE_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (double) lhsValue - (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (double) lhsValue - (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (double) lhsValue - (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (double) lhsValue - (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (double) lhsValue - (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (double) lhsValue - (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (double) lhsValue - (double) rhsValue;
					}
					break;
				case MUL:
					if (lhsType == CHAR_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (char) lhsValue * (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (char) lhsValue * (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (char) lhsValue * (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (char) lhsValue * (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (char) lhsValue * (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (char) lhsValue * (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (char) lhsValue * (double) rhsValue;
					}
					if (lhsType == BYTE_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (byte) lhsValue * (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (byte) lhsValue * (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (byte) lhsValue * (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (byte) lhsValue * (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (byte) lhsValue * (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (byte) lhsValue * (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (byte) lhsValue * (double) rhsValue;
					}
					if (lhsType == SHORT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (short) lhsValue * (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (short) lhsValue * (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (short) lhsValue * (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (short) lhsValue * (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (short) lhsValue * (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (short) lhsValue * (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (short) lhsValue * (double) rhsValue;
					}
					if (lhsType == INT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (int) lhsValue * (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (int) lhsValue * (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (int) lhsValue * (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (int) lhsValue * (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (int) lhsValue * (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (int) lhsValue * (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (int) lhsValue * (double) rhsValue;
					}
					if (lhsType == FLOAT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (float) lhsValue * (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (float) lhsValue * (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (float) lhsValue * (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (float) lhsValue * (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (float) lhsValue * (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (float) lhsValue * (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (float) lhsValue * (double) rhsValue;
					}
					if (lhsType == LONG_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (long) lhsValue * (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (long) lhsValue * (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (long) lhsValue * (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (long) lhsValue * (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (long) lhsValue * (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (long) lhsValue * (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (long) lhsValue * (double) rhsValue;
					}
					if (lhsType == DOUBLE_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (double) lhsValue * (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (double) lhsValue * (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (double) lhsValue * (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (double) lhsValue * (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (double) lhsValue * (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (double) lhsValue * (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (double) lhsValue * (double) rhsValue;
					}
					break;
				case DIV:
					if (lhsType == CHAR_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (char) lhsValue / (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (char) lhsValue / (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (char) lhsValue / (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (char) lhsValue / (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (char) lhsValue / (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (char) lhsValue / (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (char) lhsValue / (double) rhsValue;
					}
					if (lhsType == BYTE_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (byte) lhsValue / (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (byte) lhsValue / (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (byte) lhsValue / (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (byte) lhsValue / (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (byte) lhsValue / (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (byte) lhsValue / (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (byte) lhsValue / (double) rhsValue;
					}
					if (lhsType == SHORT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (short) lhsValue / (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (short) lhsValue / (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (short) lhsValue / (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (short) lhsValue / (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (short) lhsValue / (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (short) lhsValue / (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (short) lhsValue / (double) rhsValue;
					}
					if (lhsType == INT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (int) lhsValue / (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (int) lhsValue / (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (int) lhsValue / (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (int) lhsValue / (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (int) lhsValue / (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (int) lhsValue / (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (int) lhsValue / (double) rhsValue;
					}
					if (lhsType == FLOAT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (float) lhsValue / (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (float) lhsValue / (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (float) lhsValue / (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (float) lhsValue / (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (float) lhsValue / (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (float) lhsValue / (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (float) lhsValue / (double) rhsValue;
					}
					if (lhsType == LONG_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (long) lhsValue / (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (long) lhsValue / (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (long) lhsValue / (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (long) lhsValue / (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (long) lhsValue / (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (long) lhsValue / (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (long) lhsValue / (double) rhsValue;
					}
					if (lhsType == DOUBLE_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (double) lhsValue / (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (double) lhsValue / (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (double) lhsValue / (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (double) lhsValue / (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (double) lhsValue / (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (double) lhsValue / (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (double) lhsValue / (double) rhsValue;
					}
					break;
				case REM:
					if (lhsType == CHAR_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (char) lhsValue % (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (char) lhsValue % (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (char) lhsValue % (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (char) lhsValue % (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (char) lhsValue % (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (char) lhsValue % (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (char) lhsValue % (double) rhsValue;
					}
					if (lhsType == BYTE_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (byte) lhsValue % (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (byte) lhsValue % (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (byte) lhsValue % (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (byte) lhsValue % (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (byte) lhsValue % (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (byte) lhsValue % (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (byte) lhsValue % (double) rhsValue;
					}
					if (lhsType == SHORT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (short) lhsValue % (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (short) lhsValue % (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (short) lhsValue % (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (short) lhsValue % (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (short) lhsValue % (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (short) lhsValue % (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (short) lhsValue % (double) rhsValue;
					}
					if (lhsType == INT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (int) lhsValue % (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (int) lhsValue % (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (int) lhsValue % (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (int) lhsValue % (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (int) lhsValue % (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (int) lhsValue % (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (int) lhsValue % (double) rhsValue;
					}
					if (lhsType == FLOAT_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (float) lhsValue % (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (float) lhsValue % (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (float) lhsValue % (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (float) lhsValue % (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (float) lhsValue % (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (float) lhsValue % (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (float) lhsValue % (double) rhsValue;
					}
					if (lhsType == LONG_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (long) lhsValue % (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (long) lhsValue % (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (long) lhsValue % (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (long) lhsValue % (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (long) lhsValue % (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (long) lhsValue % (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (long) lhsValue % (double) rhsValue;
					}
					if (lhsType == DOUBLE_TYPE) {
						if (rhsType == CHAR_TYPE)
							result = (double) lhsValue % (char) rhsValue;
						if (rhsType == BYTE_TYPE)
							result = (double) lhsValue % (byte) rhsValue;
						if (rhsType == SHORT_TYPE)
							result = (double) lhsValue % (short) rhsValue;
						if (rhsType == INT_TYPE)
							result = (double) lhsValue % (int) rhsValue;
						if (rhsType == FLOAT_TYPE)
							result = (double) lhsValue % (float) rhsValue;
						if (rhsType == LONG_TYPE)
							result = (double) lhsValue % (long) rhsValue;
						if (rhsType == DOUBLE_TYPE)
							result = (double) lhsValue % (double) rhsValue;
					}
					break;
				default:
					throw new IllegalArgumentException("Invalid operator " + operator.name());
			}
		}
		if (result == null)
			throw new IllegalArgumentException("Result was null; invalid operand type and operator combination!");
		return new ConstantExpression(result);
	}
}
