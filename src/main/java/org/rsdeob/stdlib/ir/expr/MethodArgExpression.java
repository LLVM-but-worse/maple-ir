package org.rsdeob.stdlib.ir.expr;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.cfg.util.TypeUtils;
import org.rsdeob.stdlib.ir.stat.Statement;
import org.rsdeob.stdlib.ir.transform.impl.CodeAnalytics;

/**
 * Pseudo-synthetic expression to represent the arguments passed into a function through the local variables.
 *
 * @author ecx86
 */
public class MethodArgExpression extends Expression {

	private int index;
	private Type type;
	private boolean isStatic;

	public MethodArgExpression(int index, Type type, boolean isStatic) {
		this.index = index;
		this.type = type;
		this.isStatic = isStatic;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public boolean isThisRef() {
		return !isStatic && getIndex() == 0;
	}

	@Override
	public Type getType() {
		return type;
	}

	@Override
	public Expression copy() {
		return new MethodArgExpression(index, type, isStatic);
	}

	@Override
	public void onChildUpdated(int ptr) {
	}

	public boolean isStatic() {
		return isStatic;
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print(isThisRef() ? "this" : "arg_" + getIndex());
	}

	@Override
	public void toCode(MethodVisitor visitor, CodeAnalytics analytics) {
		visitor.visitVarInsn(TypeUtils.getVariableLoadOpcode(getType()), index);
	}

	@Override
	public boolean canChangeFlow() {
		return false;
	}

	@Override
	public boolean canChangeLogic() {
		return false;
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		return false;
	}

	@Override
	public boolean equivalent(Statement s) {
		if (!(s instanceof MethodArgExpression))
			return false;
		MethodArgExpression mae = ((MethodArgExpression) s);
		return index == mae.index && type == mae.type && (isStatic == mae.isStatic);
	}
}