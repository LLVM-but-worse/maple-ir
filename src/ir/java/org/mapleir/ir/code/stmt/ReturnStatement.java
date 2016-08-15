package org.mapleir.ir.code.stmt;

import org.mapleir.ir.code.expr.Expression;
import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.mapleir.stdlib.cfg.util.TypeUtils;
import org.mapleir.ir.analysis.dataflow.impl.CodeAnalytics;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class ReturnStatement extends Statement {

	private Type type;
	private Expression expression;

	public ReturnStatement() {
		this(Type.VOID_TYPE, null);
	}

	public ReturnStatement(Type type, Expression expression) {
		super(RETURN);
		this.type = type;
		setExpression(expression);
	}

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
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
	public void onChildUpdated(int ptr) {
		setExpression((Expression) read(ptr));
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		if (expression != null) {
			printer.print("return ");
			expression.toString(printer);
			printer.print(';');
		} else {
			printer.print("return;");
		}
	}

	@Override
	public void toCode(MethodVisitor visitor, CodeAnalytics analytics) {
		if (type != Type.VOID_TYPE) {
			expression.toCode(visitor, analytics);
			if (TypeUtils.isPrimitive(type)) {
				int[] cast = TypeUtils.getPrimitiveCastOpcodes(expression.getType(), type); // widen
				for (int i = 0; i < cast.length; i++)
					visitor.visitInsn(cast[i]);
			}
			visitor.visitInsn(TypeUtils.getReturnOpcode(type));
		} else {
			visitor.visitInsn(Opcodes.RETURN);
		}
	}

	@Override
	public boolean canChangeFlow() {
		return true;
	}

	@Override
	public boolean canChangeLogic() {
		return expression != null && expression.canChangeLogic();
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		return expression != null && expression.isAffectedBy(stmt);
	}

	@Override
	public Statement copy() {
		return new ReturnStatement(type, expression);
	}

	@Override
	public boolean equivalent(Statement s) {
		if(s instanceof ReturnStatement) {
			ReturnStatement ret = (ReturnStatement) s;
			return type.equals(ret.type) && expression.equivalent(ret.expression);
		}
		return false;
	}
}