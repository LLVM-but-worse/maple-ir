package org.mapleir.ir.code.expr;

import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.mapleir.stdlib.cfg.util.TypeUtils;
import org.mapleir.stdlib.ir.transform.impl.CodeAnalytics;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class NegationExpression extends Expression {

	private Expression expression;

	public NegationExpression(Expression expression) {
		super(NEGATE);
		setExpression(expression);
	}

	public Expression getExpression() {
		return expression;
	}

	public void setExpression(Expression expression) {
		this.expression = expression;
		overwrite(expression, 0);
	}

	@Override
	public Expression copy() {
		return new NegationExpression(expression);
	}

	@Override
	public Type getType() {
		Type t = expression.getType();
		if (t.getSort() >= Type.BOOLEAN && t.getSort() <= Type.INT) {
			return Type.INT_TYPE;
		} else if (t == Type.LONG_TYPE || t == Type.FLOAT_TYPE || t == Type.DOUBLE_TYPE) {
			return t;
		} else {
			throw new IllegalArgumentException(t.toString());
		}
	}

	@Override
	public void onChildUpdated(int ptr) {

	}
	
	@Override
	public Precedence getPrecedence0() {
		return Precedence.UNARY_PLUS_MINUS;
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		int selfPriority = getPrecedence();
		int exprPriority = expression.getPrecedence();
		printer.print('-');
		if (exprPriority > selfPriority)
			printer.print('(');
		expression.toString(printer);
		if (exprPriority > selfPriority)
			printer.print(')');
	}

	@Override
	public void toCode(MethodVisitor visitor, CodeAnalytics analytics) {
		expression.toCode(visitor, analytics);
		int[] cast = TypeUtils.getPrimitiveCastOpcodes(expression.getType(), getType());
		for (int i = 0; i < cast.length; i++)
			visitor.visitInsn(cast[i]);
		visitor.visitInsn(TypeUtils.getNegateOpcode(getType()));		
	}

	@Override
	public boolean canChangeFlow() {
		return false;
	}

	@Override
	public boolean canChangeLogic() {
		return expression.canChangeLogic();
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		return expression.isAffectedBy(stmt);
	}

	@Override
	public boolean equivalent(Statement s) {
		return (s instanceof NegationExpression && expression.equivalent(((NegationExpression)s).expression));
	}
}