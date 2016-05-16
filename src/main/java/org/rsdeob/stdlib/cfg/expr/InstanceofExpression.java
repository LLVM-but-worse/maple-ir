package org.rsdeob.stdlib.cfg.expr;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.stat.Statement;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;

public class InstanceofExpression extends Expression {

	private Expression expression;
	private Type type;

	public InstanceofExpression(Expression expression, Type type) {
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
		return type;
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
	public void toCode(MethodVisitor visitor) {
		expression.toCode(visitor);
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
}