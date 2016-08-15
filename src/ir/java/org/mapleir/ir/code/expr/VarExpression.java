package org.mapleir.ir.code.expr;

import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.ir.code.stmt.copy.CopyVarStatement;
import org.mapleir.ir.locals.Local;
import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.mapleir.stdlib.cfg.util.TypeUtils;
import org.mapleir.stdlib.ir.transform.impl.CodeAnalytics;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class VarExpression extends Expression {

	private Local local;
	private Type type;
	
	public VarExpression(Local local, Type type) {
		super(LOCAL_LOAD);
		this.local = local;
		this.type = type;
	}

	public int getIndex() {
		return local.getIndex();
	}
	
	public Local getLocal() {
		return local;
	}
	
	public void setLocal(Local local) {
		this.local = local;
	}

	@Override
	public Type getType() {
		return type;
	}
	
	public void setType(Type type) {
		this.type = type;
	}

	@Override
	public Expression copy() {
		return new VarExpression(local, type);
	}

	@Override
	public void onChildUpdated(int ptr) {
	}

	@Override
	public void toString(TabbedStringWriter printer) {
//		printer.print("(" + type + ")" + local.toString());
		printer.print(local.toString());
	}

	@Override
	public void toCode(MethodVisitor visitor, CodeAnalytics analytics) {
		if(local.isStoredInLocal()) {
			visitor.visitVarInsn(TypeUtils.getVariableLoadOpcode(getType()), local.getCodeIndex());	
		}
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
		if(stmt instanceof CopyVarStatement) {
			if(((CopyVarStatement) stmt).getVariable().getLocal() == local) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean equivalent(Statement s) {
		if(s instanceof VarExpression) {
			VarExpression var = (VarExpression) s;
			return local == var.local && type.equals(var.type);
		}
		return false;
	}
}