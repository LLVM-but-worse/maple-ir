package org.mapleir.ir.code.expr;

import org.mapleir.ir.code.stmt.Statement;
import org.objectweb.asm.Type;

public abstract class Expression extends Statement {

	public enum Precedence {
		NORMAL,
		ARRAY_ACCESS,
		METHOD_INVOCATION,
		MEMBER_ACCESS,
		UNARY_PLUS_MINUS,
		PLUS_MIN_PREPOSTFIX,
		UNARY_LOGICAL_NOT,
		UNARY_BINARY_NOT,
		CAST,
		NEW,
		MUL_DIV_REM,
		ADD_SUB,
		STRING_CONCAT,
		BITSHIFT,
		LE_LT_GE_GT_INSTANCEOF,
		EQ_NE,
		BIT_AND,
		BIT_XOR,
		BIT_OR,
		LOGICAL_AND,
		LOGICAL_OR,
		TERNARY,
		ASSIGNMENT
	}
	
	public Expression(int opcode) {
		super(opcode);
	}
	
	@Override
	public abstract void onChildUpdated(int ptr);
	
	@Override
	public abstract Expression copy();
	
	public abstract Type getType();
	
	public int getPrecedence() {
		return getPrecedence0().ordinal();
	}
	
	protected Precedence getPrecedence0() {
		return Precedence.NORMAL;
	}
	
//	@Override
//	public abstract int getExecutionCost();
}