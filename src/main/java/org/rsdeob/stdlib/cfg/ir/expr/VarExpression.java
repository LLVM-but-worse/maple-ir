package org.rsdeob.stdlib.cfg.ir.expr;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.ir.stat.CopyVarStatement;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.cfg.util.TypeUtils;

/**
 * Expression that represents loading of a stack or local variable.
 * 
 * @author Bibl, ecx86
 */
public class VarExpression extends Expression {

	private int index;
	private Type type;
	private boolean stackVariable;
	
	public VarExpression(int index, Type type, boolean stackVariable) {
		this.index = index;
		this.type = type;
		this.stackVariable = stackVariable;
	}
	
	public VarExpression(int index, Type type) {
		this(index, type, false);
	}

	public boolean isStackVariable() {
		return stackVariable;
	}

	public void setStackVariable(boolean stackVariable) {
		this.stackVariable = stackVariable;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
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
		return new VarExpression(index, type, stackVariable);
	}

	@Override
	public void onChildUpdated(int ptr) {
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print((stackVariable ? "s" : "l") + "var" + index);		
	}

	@Override
	public void toCode(MethodVisitor visitor) {
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
		for(int i=0; stmt.read(i) != null; i++) {
			if(isAffectedBy(stmt.read(i))) {
				return true;
			}
			
			if(stmt instanceof CopyVarStatement) {
				if(((CopyVarStatement) stmt).getIndex() == index) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		VarExpression that = (VarExpression) o;

		if (index != that.index) return false;
		return stackVariable == that.stackVariable;

	}

	@Override
	public int hashCode() {
		int result = index;
		result = 31 * result + (stackVariable ? 1 : 0);
		return result;
	}
}