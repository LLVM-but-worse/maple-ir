package org.rsdeob.stdlib.cfg.stat;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.expr.Expression;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.cfg.util.TypeUtils;

public class PopStatement extends Statement {

	private Expression expression;
	
	public PopStatement(Expression expression) {
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
	public void onChildUpdated(int ptr) {
		setExpression((Expression)read(ptr));
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print("pop(");
		expression.toString(printer);
		printer.print(");");		
	}

	@Override
	public void toCode(MethodVisitor visitor) {
		expression.toCode(visitor);
		if (expression.getType() != Type.VOID_TYPE)
			visitor.visitInsn(TypeUtils.getPopOpcode(expression.getType()));	
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