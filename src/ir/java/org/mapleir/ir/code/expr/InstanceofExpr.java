package org.mapleir.ir.code.expr;

import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class InstanceofExpr extends Expr {

	private Expr expression;
	private Type type;

	public InstanceofExpr(Expr expression, Type type) {
		super(INSTANCEOF);
		setExpression(expression);
		this.type = type;
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
		return new InstanceofExpr(expression.copy(), type);
	}

	@Override
	public Type getType() {
		return Type.BOOLEAN_TYPE;
	}

	public void setType(Type type) {
		this.type = type;
	}

	@Override
	public void onChildUpdated(int ptr) {
		setExpression((Expr) read(0));
	}
	
	@Override
	public Precedence getPrecedence0() {
		return Precedence.LE_LT_GE_GT_INSTANCEOF;
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
		printer.print(" instanceof ");
		printer.print(type.getClassName());
	}

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		expression.toCode(visitor, cfg);
		visitor.visitTypeInsn(Opcodes.INSTANCEOF, type.getInternalName());
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
	public boolean isAffectedBy(CodeUnit stmt) {
		return expression.isAffectedBy(stmt);
	}

	@Override
	public boolean equivalent(CodeUnit s) {
		if(s instanceof InstanceofExpr) {
			InstanceofExpr e = (InstanceofExpr) s;
			return expression.equivalent(e.expression) && type.equals(e.type);
		}
		return false;
	}
}