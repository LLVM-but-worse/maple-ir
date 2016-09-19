package org.mapleir.ir.code.stmt.copy;

import org.mapleir.ir.code.expr.Expression;
import org.mapleir.ir.code.expr.PhiExpression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.Statement;

public class CopyPhiStatement extends AbstractCopyStatement {

	public CopyPhiStatement(VarExpression variable, PhiExpression expression) {
		super(PHI_STORE, variable, expression);
	}

	public CopyPhiStatement(VarExpression variable, PhiExpression expression, boolean synthetic) {
		super(PHI_STORE, variable, expression, synthetic);
	}
	
	@Override
	public PhiExpression getExpression() {
		return (PhiExpression) super.getExpression();
	}
	
	@Override
	public void setExpression(Expression expression) {
		if(!(expression instanceof PhiExpression)) {
			throw new UnsupportedOperationException(expression.toString());
		}
		
		super.setExpression(expression);
	}

	@Override
	public CopyPhiStatement copy() {
		return new CopyPhiStatement(getVariable().copy(), getExpression().copy(), isSynthetic());
	}

	@Override
	public boolean equivalent(Statement s) {
		if(s instanceof CopyPhiStatement) {
			CopyPhiStatement copy = (CopyPhiStatement) s;
			return getExpression().equivalent(copy.getExpression()) && getVariable().equivalent(copy.getVariable());
		}
		return false;
	}
}