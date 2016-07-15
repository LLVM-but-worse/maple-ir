package org.rsdeob.stdlib.ir.stat;

import org.rsdeob.stdlib.ir.expr.Expression;
import org.rsdeob.stdlib.ir.expr.MethodArgExpression;
import org.rsdeob.stdlib.ir.expr.VarExpression;

public class SyntheticCopyStatement extends CopyVarStatement{

	private boolean isStatic;

	public SyntheticCopyStatement(VarExpression var, boolean isStatic) {
		super(var, new MethodArgExpression(var.getIndex(), var.getType(), isStatic));
		this.isStatic = isStatic;
	}

	@Override
	protected void overwriteExpression() {
	}

	@Override
	public void setExpression(Expression expression) {
		if (!(expression instanceof MethodArgExpression))
			throw new IllegalArgumentException("Expression in a synthetic def must be a MethodArgExpression");
		this.expression = expression;
		this.isStatic = ((MethodArgExpression) expression).isStatic();
	}

	@Override
	public void onChildUpdated(int ptr) {
	}

	@Override
	public String toString() {
		return "synth(" + variable + " = " + expression + ");";
	}

	@Override
	public Statement copy() {
		return new SyntheticCopyStatement(variable, isStatic);
	}
}