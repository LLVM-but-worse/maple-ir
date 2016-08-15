package org.mapleir.ir.code.expr;

import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.mapleir.stdlib.ir.transform.impl.CodeAnalytics;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class InstanceofExpression extends Expression {

	private Expression expression;
	private Type type;

	public InstanceofExpression(Expression expression, Type type) {
		super(INSTANCEOF);
		setExpression(expression);
		this.type = type;
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
		return new InstanceofExpression(expression, type);
	}

	@Override
	public Type getType() {
		return Type.BOOLEAN_TYPE;
	}

	public void setType(Type type) {
		this.type = type;
	}

	@Override
	public void onChildUpdated(int ptr) {
		setExpression((Expression) read(0));
	}
	
	@Override
	public Precedence getPrecedence0() {
		return Precedence.LE_LT_GE_GT_INSTANCEOF;
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		int selfPriority = getPrecedence();
		int expressionPriority = expression.getPrecedence();
		if (expressionPriority > selfPriority)
			printer.print('(');
		expression.toString(printer);
		if (expressionPriority > selfPriority)
			printer.print(')');
		printer.print(" instanceof ");
		printer.print(type.getClassName());
	}

	@Override
	public void toCode(MethodVisitor visitor, CodeAnalytics analytics) {
		expression.toCode(visitor, analytics);
		visitor.visitTypeInsn(Opcodes.INSTANCEOF, type.getInternalName());
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
		if(s instanceof InstanceofExpression) {
			InstanceofExpression e = (InstanceofExpression) s;
			return expression.equivalent(e.expression) && type.equals(e.type);
		}
		return false;
	}
}