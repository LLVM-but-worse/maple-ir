package org.mapleir.ir.code.expr;

import org.mapleir.ir.TypeUtils;
import org.mapleir.ir.TypeUtils.ArrayType;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.codegen.BytecodeFrontend;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class ArrayLoadExpr extends Expr {
	
	private Expr array;
	private Expr index;
	private ArrayType type;

	public ArrayLoadExpr(Expr array, Expr index, ArrayType type) {
		super(ARRAY_LOAD);
		setArrayExpression(array);
		setIndexExpression(index);
		this.type = type;
	}

	public Expr getArrayExpression() {
		return array;
	}

	public void setArrayExpression(Expr arrayExpression) {
		array = arrayExpression;
		overwrite(arrayExpression, 0);
	}

	public Expr getIndexExpression() {
		return index;
	}

	public void setIndexExpression(Expr indexExpression) {
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
	public Expr copy() {
		return new ArrayLoadExpr(array.copy(), index.copy(), type);
	}

	@Override
	public Type getType() {
		return type.getType();
	}

	@Override
	public void onChildUpdated(int ptr) {
		if (ptr == 0) {
			setArrayExpression(read(0));
		} else if (ptr == 1) {
			setIndexExpression(read(1));
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
		index.toString(printer);
		printer.print(']');
	}

	@Override
	public void toCode(MethodVisitor visitor, BytecodeFrontend assembler) {
		array.toCode(visitor, assembler);
		index.toCode(visitor, assembler);
//		System.out.println("the:  " + index.getId() + ". "+ index);
//		System.out.println("  par:   " + getRootParent().getId() + ". "+ getRootParent());
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
	public boolean equivalent(CodeUnit s) {
		if(s instanceof ArrayLoadExpr) {
			ArrayLoadExpr load = (ArrayLoadExpr) s;
			return array.equals(load.array) && index.equals(load.index);
		}
		return false;
	}
}