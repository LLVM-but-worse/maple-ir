package org.mapleir.ir.code.stmt.copy;

import org.mapleir.ir.code.expr.Expression;
import org.mapleir.ir.code.expr.VarExpression;
import org.mapleir.ir.code.stmt.Statement;

public class CopyVarStatement extends AbstractCopyStatement {

	public CopyVarStatement(VarExpression variable, Expression expression) {
		super(LOCAL_STORE, variable, expression);
	}
	
	public CopyVarStatement(VarExpression variable, Expression expression, boolean synthetic) {
		super(LOCAL_STORE, variable, expression, synthetic);
	}

	@Override
	public Statement copy() {
		return new CopyVarStatement(getVariable(), getExpression(), isSynthetic());
	}

	@Override
	public boolean equivalent(Statement s) {
		if(s instanceof CopyVarStatement) {
			CopyVarStatement copy = (CopyVarStatement) s;
			return getExpression().equivalent(copy.getExpression()) && getVariable().equivalent(copy.getVariable());
		}
		return false;
	}
}