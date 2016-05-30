package org.rsdeob.stdlib.cfg.ir.stat;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.expr.VarExpression;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.cfg.util.TypeUtils;

public class CopyVarStatement extends Statement implements IStackDumpNode {

	private Expression expression;
	private VarExpression variable;

	public CopyVarStatement(VarExpression variable, Expression expression) {
		if (variable == null | expression == null)
			throw new IllegalArgumentException("Neither variable nor statement can be null!");

		this.expression = expression;
		this.variable = variable;
		
		overwrite(expression, 0);
		overwrite(variable, 1);
	}

	@Override
	public Expression getExpression() {
		return expression;
	}

	public VarExpression getVariable() {
		return variable;
	}

	@Override
	public void setExpression(Expression expression) {
		this.expression = expression;
		overwrite(expression, 0);
	}

	public void setVariable(VarExpression var) {
		variable = var;
		overwrite(var, 1);
	}
	
	// FIXME: should these really be delegating calls
	
	@Override
	public int getIndex() {
		return variable.getIndex();
	}

	@Override
	public void setIndex(int index) {
		variable.setIndex(index);
	}

	@Override
	public Type getType() {
		return variable.getType();
	}

	@Override
	public void setType(Type type) {
		variable.setType(type);
	}

	@Override
	public void onChildUpdated(int ptr) {
		if(ptr == 0) {
			setExpression((Expression) read(ptr));
		} else if(ptr == 1) {
			setVariable((VarExpression) read(ptr));
		}
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
	public void toCode(MethodVisitor visitor) {
		// FIXME: rework because of VariableExpression
		expression.toCode(visitor);
		Type type = variable.getType();
		if (TypeUtils.isPrimitive(type)) {
			int[] cast = TypeUtils.getPrimitiveCastOpcodes(expression.getType(), type);
			for (int i = 0; i < cast.length; i++)
				visitor.visitInsn(cast[i]);
		}
		variable.toCode(visitor);
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
	public boolean isRedundant() {
		if(expression instanceof VarExpression) {
			return ((VarExpression) expression).getIndex() == variable.getIndex();
		} else {
			return false;
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		CopyVarStatement that = (CopyVarStatement) o;

		if (!expression.equals(that.expression)) return false;
		return variable.equals(that.variable);

	}

	@Override
	public int hashCode() {
		int result = getId().hashCode();
		result = 31 * result + expression.hashCode();
		result = 31 * result + variable.hashCode();
		return result;
	}
}