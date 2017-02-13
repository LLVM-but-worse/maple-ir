package org.mapleir.ir.code.stmt.copy;

import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.expr.VarExpr;

public class CopyVarStatement extends AbstractCopyStatement {

	public CopyVarStatement(VarExpr variable, Expr expression) {
		super(LOCAL_STORE, variable, expression);
	}
	
	public CopyVarStatement(VarExpr variable, Expr expression, boolean synthetic) {
		super(LOCAL_STORE, variable, expression, synthetic);
	}

	@Override
	public CopyVarStatement copy() {
		return new CopyVarStatement(getVariable().copy(), getExpression().copy(), isSynthetic());
	}

	@Override
	public boolean equivalent(CodeUnit s) {
		if(s instanceof CopyVarStatement) {
			CopyVarStatement copy = (CopyVarStatement) s;
			return getExpression().equivalent(copy.getExpression()) && getVariable().equivalent(copy.getVariable());
		}
		return false;
	}
}