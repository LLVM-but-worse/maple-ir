package org.mapleir.ir.code.stmt;

import org.mapleir.app.service.TypeUtils;
import org.mapleir.app.service.TypeUtils.ArrayType;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Expr.Precedence;
import org.mapleir.ir.code.Stmt;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class ArrayStoreStmt extends Stmt {

	private Expr arrayExpression;
	private Expr indexExpression;
	private Expr valueExpression;
	private ArrayType type;

	public ArrayStoreStmt(Expr arrayExpression, Expr indexExpression, Expr valueExpression, ArrayType type) {
		super(ARRAY_STORE);
		setArrayExpression(arrayExpression);
		setIndexExpression(indexExpression);
		setValueExpression(valueExpression);
		this.type = type;
	}

	public Expr getArrayExpression() {
		return arrayExpression;
	}

	public void setArrayExpression(Expr arrayExpression) {
		this.arrayExpression = arrayExpression;
		overwrite(arrayExpression, 0);
	}

	public Expr getIndexExpression() {
		return indexExpression;
	}

	public void setIndexExpression(Expr indexExpression) {
		this.indexExpression = indexExpression;
		overwrite(indexExpression, 1);
	}

	public Expr getValueExpression() {
		return valueExpression;
	}

	public void setValueExpression(Expr valueExpression) {
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
			setArrayExpression(read(0));
		} else if (ptr == 1) {
			setIndexExpression(read(1));
		} else if (ptr == 2) {
			setValueExpression(read(2));
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
//			System.out.println(this);
//			System.out.println(valueExpression.getType() + " -> " + type.getType());
			int[] vCast = TypeUtils.getPrimitiveCastOpcodes(valueExpression.getType(), type.getType());
//			System.out.println("vcast: " + Arrays.toString(vCast));
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
	public ArrayStoreStmt copy() {
		return new ArrayStoreStmt(arrayExpression.copy(), indexExpression.copy(), valueExpression.copy(), type);
	}

	@Override
	public boolean equivalent(CodeUnit s) {
		if (s instanceof ArrayStoreStmt) {
			ArrayStoreStmt store = (ArrayStoreStmt) s;
			return arrayExpression.equivalent(store.arrayExpression)
					&& indexExpression.equivalent(store.indexExpression)
					&& valueExpression.equivalent(store.valueExpression) && type.equals(store.type);
		}
		return false;
	}
}