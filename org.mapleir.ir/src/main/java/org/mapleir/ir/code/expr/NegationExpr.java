package org.mapleir.ir.code.expr;

import org.mapleir.ir.TypeUtils;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class NegationExpr extends Expr {

	private Expr expression;

	public NegationExpr(Expr expression) {
		super(NEGATE);
		setExpression(expression);
	}

	public Expr getExpression() {
		return expression;
	}

	public void setExpression(Expr expression) {
		this.expression = expression;
		overwrite(expression, 0);
	}

	@Override
	public Expr copy() {
		return new NegationExpr(expression.copy());
	}

	@Override
	public Type getType() {
		Type t = expression.getType();
		if (t.getSort() >= Type.BOOLEAN && t.getSort() <= Type.INT) {
			return Type.INT_TYPE;
		} else if (t == Type.LONG_TYPE || t == Type.FLOAT_TYPE || t == Type.DOUBLE_TYPE) {
			return t;
		} else {
			throw new IllegalArgumentException(t.toString());
		}
	}

	@Override
	public void onChildUpdated(int ptr) {
		setExpression(read(ptr));
	}
	
	@Override
	public Precedence getPrecedence0() {
		return Precedence.UNARY_PLUS_MINUS;
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		int selfPriority = getPrecedence();
		int exprPriority = expression.getPrecedence();
		printer.print('-');
		if (exprPriority > selfPriority)
			printer.print('(');
		expression.toString(printer);
		if (exprPriority > selfPriority)
			printer.print(')');
	}

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		expression.toCode(visitor, cfg);
		int[] cast = TypeUtils.getPrimitiveCastOpcodes(expression.getType(), getType());
		for (int i = 0; i < cast.length; i++)
			visitor.visitInsn(cast[i]);
		visitor.visitInsn(TypeUtils.getNegateOpcode(getType()));		
	}

	@Override
	public boolean canChangeFlow() {
		return false;
	}

	@Override
	public boolean equivalent(CodeUnit s) {
		return (s instanceof NegationExpr && expression.equivalent(((NegationExpr)s).expression));
	}
}