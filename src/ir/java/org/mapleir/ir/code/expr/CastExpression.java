package org.mapleir.ir.code.expr;

import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.mapleir.stdlib.util.TypeUtils;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class CastExpression extends Expression {

	private Expression expression;
	private Type type;

	public CastExpression(Expression expression, Type type) {
		super(CAST);
		setExpression(expression);
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
	public Expression copy() {
		return new CastExpression(expression.copy(), type);
	}

	@Override
	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	@Override
	public void onChildUpdated(int ptr) {
		setExpression((Expression) read(ptr));
	}

	@Override
	public Precedence getPrecedence0() {
		return Precedence.CAST;
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		int selfPriority = getPrecedence();
		int exprPriority = expression.getPrecedence();
		printer.print('(');
		printer.print(type.getClassName());
		printer.print(')');
		if (exprPriority > selfPriority) {
			printer.print('(');
		}
		expression.toString(printer);
		if (exprPriority > selfPriority) {
			printer.print(')');
		}
	}

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		expression.toCode(visitor, cfg);
		if (TypeUtils.isObjectRef(getType())) {
			visitor.visitTypeInsn(Opcodes.CHECKCAST, type.getInternalName());
		} else {
			int[] instructions = TypeUtils.getPrimitiveCastOpcodes(expression.getType(), type);
			for (int i = 0; i < instructions.length; i++) {
				visitor.visitInsn(instructions[i]);
			}
		}
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

	@Override
	public boolean equivalent(Statement s) {
		if(s instanceof CastExpression) {
			CastExpression cast = (CastExpression) s;
			return expression.equivalent(cast) && type.equals(cast.type);
		}
		return false;
	}
}