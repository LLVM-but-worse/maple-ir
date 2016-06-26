package org.rsdeob.stdlib.cfg.ir.stat;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.ir.Local;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.ir.transform.impl.CodeAnalytics;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.cfg.util.TypeUtils;

public class CopyVarStatement extends Statement {

	private Expression expression;
	private VarExpression variable;
	
	public CopyVarStatement(VarExpression variable, Expression expression) {
		if (variable == null | expression == null)
			throw new IllegalArgumentException("Neither variable nor statement can be null!");

		this.expression = expression;
		this.variable = variable;
		
		overwrite(expression, 0);
	}

	public Expression getExpression() {
		return expression;
	}

	public VarExpression getVariable() {
		return variable;
	}

	public void setVariable(VarExpression var) {
		variable = var;
	}
	
	public void setExpression(Expression expression) {
		this.expression = expression;
		overwrite(expression, 0);
	}
	
	public int getIndex() {
		return variable.getLocal().getIndex();
	}

	public Type getType() {
		return variable.getType();
	}

	@Override
	public void onChildUpdated(int ptr) {
		setExpression((Expression) read(ptr));
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print(toString());
	}

	@Override
	public String toString() {
		return variable + " = " + expression + ";";
	}

	@Override
	// todo: this probably needs a refactoring
	public void toCode(MethodVisitor visitor, CodeAnalytics analytics) {
		if(expression instanceof VarExpression) {
			if(((VarExpression) expression).getLocal() == variable.getLocal()) {
				return;
			}
		}
		
		variable.getLocal().setTempLocal(false);
		
		expression.toCode(visitor, analytics);
		Type type = variable.getType();
		if (TypeUtils.isPrimitive(type)) {
			int[] cast = TypeUtils.getPrimitiveCastOpcodes(expression.getType(), type);
			for (int i = 0; i < cast.length; i++)
				visitor.visitInsn(cast[i]);
		}

		Local local = variable.getLocal();
		if(local.isStack()) {
			visitor.visitVarInsn(TypeUtils.getVariableStoreOpcode(getType()), variable.getLocal().getCodeIndex());
			variable.getLocal().setTempLocal(true);
		} else {
			visitor.visitVarInsn(TypeUtils.getVariableStoreOpcode(getType()), variable.getLocal().getCodeIndex());
		}
	}

	@Override
	public boolean canChangeFlow() {
		return false;
	}

	@Override
	public boolean canChangeLogic() {
		return expression.canChangeLogic() || variable.isAffectedBy(this);
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		return expression.isAffectedBy(stmt);
	}

	public boolean isRedundant() {
		if(expression instanceof VarExpression) {
			return ((VarExpression) expression).getLocal().getIndex() == variable.getLocal().getIndex();
		} else {
			return false;
		}
	}

	public boolean valueEquals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		CopyVarStatement that = (CopyVarStatement) o;

		if (!expression.equals(that.expression)) return false;
		return variable.equals(that.variable);

	}

	@Override
	public Statement copy() {
		return new CopyVarStatement(variable, expression);
	}

	@Override
	public boolean equivalent(Statement s) {
		if(s instanceof CopyVarStatement) {
			CopyVarStatement copy = (CopyVarStatement) s;
			return expression.equivalent(copy.expression) && variable.equivalent(copy.variable);
		}
		return false;
	}
}