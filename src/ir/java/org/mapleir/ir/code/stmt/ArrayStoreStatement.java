package org.mapleir.ir.code.stmt;

import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.expr.Expression;
import org.mapleir.ir.code.expr.Expression.Precedence;
import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.mapleir.stdlib.cfg.util.TypeUtils;
import org.mapleir.stdlib.cfg.util.TypeUtils.ArrayType;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class ArrayStoreStatement extends Statement {

	private Expression arrayExpression;
	private Expression indexExpression;
	private Expression valueExpression;
	private ArrayType type;
	
	public ArrayStoreStatement(Expression arrayExpression, Expression indexExpression, Expression valueExpression, ArrayType type) {
		super(ARRAY_STORE);
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
		valueExpression.toString(printer);
		printer.print(';');
	}

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		arrayExpression.toCode(visitor, cfg);
		indexExpression.toCode(visitor, cfg);
		int[] iCast = TypeUtils.getPrimitiveCastOpcodes(indexExpression.getType(), Type.INT_TYPE); // widen
		for (int i = 0; i < iCast.length; i++)
			visitor.visitInsn(iCast[i]);
		valueExpression.toCode(visitor, cfg);
		if (TypeUtils.isPrimitive(type.getType())) {
			int[] vCast = TypeUtils.getPrimitiveCastOpcodes(valueExpression.getType(), type.getType());
			for (int i = 0; i < vCast.length; i++)
				visitor.visitInsn(vCast[i]);
		}
		visitor.visitInsn(type.getStoreOpcode());
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
		return stmt.canChangeLogic() || 
				arrayExpression.isAffectedBy(stmt) || 
				indexExpression.isAffectedBy(stmt) || 
				valueExpression.isAffectedBy(stmt);
	}

	@Override
	public Statement copy() {
		return new ArrayStoreStatement(arrayExpression, indexExpression, valueExpression, type);
	}

	@Override
	public boolean equivalent(Statement s) {
		if(s instanceof ArrayStoreStatement) {
			ArrayStoreStatement store = (ArrayStoreStatement) s;
			return arrayExpression.equivalent(store.arrayExpression) && indexExpression.equivalent(store.indexExpression) &&
					valueExpression.equivalent(store.valueExpression) && type.equals(store.type);
		}
		return false;
	}
}