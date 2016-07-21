package org.mapleir.stdlib.ir.expr;

import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.mapleir.stdlib.ir.stat.Statement;
import org.mapleir.stdlib.ir.transform.impl.CodeAnalytics;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class UninitialisedObjectExpression extends Expression {

	private Type type;

	public UninitialisedObjectExpression(Type type) {
		this.type = type;
	}

	@Override
	public Expression copy() {
		return new UninitialisedObjectExpression(type);
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
		
	}
	
	@Override
	public Precedence getPrecedence0() {
		return Precedence.NEW;
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print("new " + type.getClassName());		
	}

	@Override
	public void toCode(MethodVisitor visitor, CodeAnalytics analytics) {
		visitor.visitTypeInsn(Opcodes.NEW, type.getInternalName());		
	}

	@Override
	public boolean canChangeFlow() {
		return false;
	}

	@Override
	public boolean canChangeLogic() {
		return true;
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		return false;
	}

	@Override
	public boolean equivalent(Statement s) {
		return s instanceof UninitialisedObjectExpression && type.equals(((UninitialisedObjectExpression) s).type);
	}
}