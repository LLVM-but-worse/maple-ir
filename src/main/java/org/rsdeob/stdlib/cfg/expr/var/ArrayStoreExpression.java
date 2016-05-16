package org.rsdeob.stdlib.cfg.expr.var;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.expr.Expression;
import org.rsdeob.stdlib.cfg.stat.Statement;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.cfg.util.TypeUtils;
import org.rsdeob.stdlib.cfg.util.TypeUtils.ArrayType;

public class ArrayStoreExpression extends Expression {

	private Expression arrayExpression;
	private Expression indexExpression;
	private Expression valueExpression;
	private ArrayType type;

	public ArrayStoreExpression(Expression arrayExpression, Expression indexExpression, Expression valueExpression, ArrayType type) {
		setArrayExpression(arrayExpression);
		setIndexExpression(indexExpression);
		setValueExpression(valueExpression);
		this.type = type;
	}

	public Expression getArrayExpression() {
		return arrayExpression;
	}

	public void setArrayExpression(Expression arrayExpression) {
		this.arrayExpression = arrayExpression;
		overwrite(arrayExpression, 0);
	}

	public Expression getIndexExpression() {
		return indexExpression;
	}

	public void setIndexExpression(Expression indexExpression) {
		this.indexExpression = indexExpression;
		overwrite(indexExpression, 1);
	}

	public Expression getValueExpression() {
		return valueExpression;
	}

	public void setValueExpression(Expression valueExpression) {
		this.valueExpression = valueExpression;
		overwrite(valueExpression, 2);
	}

	public ArrayType getArrayType() {
		return type;
	}

	public void setArrayType(ArrayType type) {
		this.type = type;
	}

	@Override
	public void onChildUpdated(int ptr) {
		if (ptr == 0) {
			setArrayExpression((Expression) read(0));
		} else if (ptr == 1) {
			setIndexExpression((Expression) read(1));
		} else if (ptr == 2) {
			setValueExpression((Expression) read(2));
		}
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		int accessPriority = Precedence.ARRAY_ACCESS.ordinal();
		int basePriority = arrayExpression.getPrecedence();
		if (basePriority > accessPriority)
			printer.print('(');
		arrayExpression.toString(printer);
		if (basePriority > accessPriority)
			printer.print(')');
		printer.print('[');
		indexExpression.toString(printer);
		printer.print(']');
		printer.print(" = ");
		int selfPriority = getPrecedence();
		int expressionPriority = valueExpression.getPrecedence();
		if (expressionPriority > selfPriority)
			printer.print('(');
		valueExpression.toString(printer);
		if (expressionPriority > selfPriority)
			printer.print(')');
	}

	@Override
	public void toCode(MethodVisitor visitor) {
		arrayExpression.toCode(visitor);
		indexExpression.toCode(visitor);
		int[] iCast = TypeUtils.getPrimitiveCastOpcodes(indexExpression.getType(), Type.INT_TYPE); // widen
		for (int i = 0; i < iCast.length; i++)
			visitor.visitInsn(iCast[i]);
		valueExpression.toCode(visitor);
		if (TypeUtils.isPrimitive(type.getType())) {
			int[] vCast = TypeUtils.getPrimitiveCastOpcodes(valueExpression.getType(), type.getType());
			for (int i = 0; i < vCast.length; i++)
				visitor.visitInsn(vCast[i]);
		}
		// double or long doesn't matter we need to get dupX_X2
		visitor.visitInsn(TypeUtils.getDupXOpcode(getType(), Type.DOUBLE_TYPE));
		visitor.visitInsn(type.getStoreOpcode());
	}

	@Override
	public Expression copy() {
		return new ArrayStoreExpression(arrayExpression, indexExpression, valueExpression, type);
	}

	@Override
	public Type getType() {
		return type.getType();
	}

	@Override
	public boolean canChangeFlow() {
		return false;
	}

	@Override
	public boolean canChangeLogic() {
		return true;
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		return stmt.canChangeLogic() || arrayExpression.isAffectedBy(stmt) || indexExpression.isAffectedBy(stmt) || valueExpression.isAffectedBy(stmt);
	}
}