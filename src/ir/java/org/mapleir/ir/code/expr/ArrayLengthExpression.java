package org.mapleir.ir.code.expr;

import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class ArrayLengthExpression extends Expression {

	private Expression expression;

	public ArrayLengthExpression(Expression expression) {
		super(ARRAY_LEN);
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
		return new ArrayLengthExpression(expression.copy());
	}

	@Override
	public Type getType() {
		return Type.INT_TYPE;
	}

	@Override
	public void onChildUpdated(int ptr) {
		setExpression((Expression) read(0));
	}

	@Override
	public Precedence getPrecedence0() {
		return Precedence.MEMBER_ACCESS;
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
		printer.print(".length");
	}

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		expression.toCode(visitor, cfg);
		visitor.visitInsn(Opcodes.ARRAYLENGTH);
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
		return stmt.canChangeLogic() || expression.isAffectedBy(stmt);
	}

	@Override
	public boolean equivalent(Statement s) {
		return (s instanceof ArrayLengthExpression) && expression.equivalent(((ArrayLengthExpression)s).expression);
	}
}