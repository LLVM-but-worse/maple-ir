package org.rsdeob.stdlib.cfg.ir.expr;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;

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
	public void toCode(MethodVisitor visitor) {
		visitor.visitTypeInsn(Opcodes.NEW, type.getInternalName());		
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
}