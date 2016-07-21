package org.mapleir.stdlib.ir.expr;

import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.mapleir.stdlib.ir.stat.Statement;
import org.mapleir.stdlib.ir.transform.impl.CodeAnalytics;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class CaughtExceptionExpression extends Expression {

	private Type type;

	private CaughtExceptionExpression(Type type) {
		this.type = type;
	}

	public CaughtExceptionExpression(String type) {
		if (type == null) {
			type = "Ljava/lang/Throwable;";
		}
		this.type = Type.getType(type);
	}

	@Override
	public Expression copy() {
		return new CaughtExceptionExpression(type);
	}

	@Override
	public Type getType() {
		return type;
	}

	@Override
	public void onChildUpdated(int ptr) {

	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print("catch()");
	}

	@Override
	public void toCode(MethodVisitor visitor, CodeAnalytics analytics) {
		
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
		return true;
	}

	@Override
	public boolean equivalent(Statement s) {
		return (s instanceof CaughtExceptionExpression && type.equals(((CaughtExceptionExpression) s).type));
	}
}