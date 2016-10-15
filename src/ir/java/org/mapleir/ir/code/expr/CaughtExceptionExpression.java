package org.mapleir.ir.code.expr;

import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class CaughtExceptionExpression extends Expression {

	private Type type;
	
	private CaughtExceptionExpression(Type type) {
		super(CATCH);
		this.type = type;
	}

	public CaughtExceptionExpression(String type) {
		super(CATCH);
		if (type == null) {
			type = "Ljava/lang/Throwable;";
		} else {
			type = "L" + type + ";";
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
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		
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
		if(s.getOpcode() == CATCH) {
			CaughtExceptionExpression e = (CaughtExceptionExpression) s;
			return type.equals(e.type);
		} else {
			return false;
		}
	}
}