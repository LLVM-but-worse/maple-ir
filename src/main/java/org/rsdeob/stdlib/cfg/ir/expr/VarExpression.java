package org.rsdeob.stdlib.cfg.ir.expr;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.ir.Local;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.ir.transform.impl.CodeAnalytics;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.cfg.util.TypeUtils;

/**
 * Expression that represents loading of a stack or local variable.
 * 
 * @author Bibl, ecx86
 */
public class VarExpression extends Expression {

	private Local local;
	private Type type;
	
	public VarExpression(Local local, Type type) {
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

	@Override
	public Expression copy() {
		return new VarExpression(local, type);
	}

	@Override
	public void onChildUpdated(int ptr) {
	}

	@Override
	public void toString(TabbedStringWriter printer) {
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