package org.mapleir.ir.code.stmt;

import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.expr.Expression;
import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.mapleir.stdlib.cfg.util.TypeUtils;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class PopStatement extends Statement {

	private Expression expression;
	
	public PopStatement(Expression expression) {
		super(POP);
		setExpression(expression);
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
		setExpression((Expression)read(ptr));
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print("pop(");
		expression.toString(printer);
		printer.print(");");		
	}

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		expression.toCode(visitor, cfg);
		if (expression.getType() != Type.VOID_TYPE)
			visitor.visitInsn(TypeUtils.getPopOpcode(expression.getType()));	
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
	public Statement copy() {
		return new PopStatement(expression.copy());
	}

	@Override
	public boolean equivalent(Statement s) {
		return s instanceof PopStatement && expression.equivalent(((PopStatement) s).expression);
	}
}