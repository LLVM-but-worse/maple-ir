package org.mapleir.ir.code.expr;

import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.mapleir.stdlib.cfg.util.TypeUtils;
import org.mapleir.stdlib.cfg.util.TypeUtils.ArrayType;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class ArrayLoadExpression extends Expression {
	
	private Expression array;
	private Expression index;
	private ArrayType type;

	public ArrayLoadExpression(Expression array, Expression index, ArrayType type) {
		super(ARRAY_LOAD);
		setArrayExpression(array);
		setIndexExpression(index);
		this.type = type;
	}

	public Expression getArrayExpression() {
		return array;
	}

	public void setArrayExpression(Expression arrayExpression) {
		array = arrayExpression;
		overwrite(arrayExpression, 0);
	}

	public Expression getIndexExpression() {
		return index;
	}

	public void setIndexExpression(Expression indexExpression) {
		index = indexExpression;
		overwrite(indexExpression, 1);
	}

	public ArrayType getArrayType() {
		return type;
	}

	public void setArrayType(ArrayType type) {
		this.type = type;
	}

	@Override
	public Expression copy() {
		return new ArrayLoadExpression(array.copy(), index.copy(), type);
	}

	@Override
	public Type getType() {
		return type.getType();
	}

	@Override
	public void onChildUpdated(int ptr) {
		if (ptr == 0) {
			setArrayExpression((Expression) read(0));
		} else if (ptr == 1) {
			setIndexExpression((Expression) read(1));
		}
	}

	@Override
	public Precedence getPrecedence0() {
		return Precedence.ARRAY_ACCESS;
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		int selfPriority = getPrecedence();
		int expressionPriority = array.getPrecedence();
		if (expressionPriority > selfPriority) {
			printer.print('(');
		}
		array.toString(printer);
		if (expressionPriority > selfPriority) {
			printer.print(')');
		}
		printer.print('[');
		printer.print("(" + index.getType() + ")");
		index.toString(printer);
		printer.print(']');
	}

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		array.toCode(visitor, cfg);
		index.toCode(visitor, cfg);
		System.out.println(getId() + ". "+ this);
		int[] iCast = TypeUtils.getPrimitiveCastOpcodes(index.getType(), Type.INT_TYPE);
		for (int i = 0; i < iCast.length; i++) {
			visitor.visitInsn(iCast[i]);
		}
		visitor.visitInsn(type.getLoadOpcode());
	}

	@Override
	public boolean canChangeFlow() {
		return false;
	}

	@Override
	public boolean canChangeLogic() {
		return array.canChangeLogic() || index.canChangeLogic();
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		return stmt.canChangeLogic() || array.isAffectedBy(stmt) || index.isAffectedBy(stmt);
	}

	@Override
	public boolean equivalent(Statement s) {
		if(s instanceof ArrayLoadExpression) {
			ArrayLoadExpression load = (ArrayLoadExpression) s;
			return array.equals(load.array) && index.equals(load.index);
		}
		return false;
	}
}