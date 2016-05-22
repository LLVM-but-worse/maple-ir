package org.rsdeob.stdlib.cfg.expr;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.stat.StackDumpStatement;
import org.rsdeob.stdlib.cfg.stat.Statement;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.cfg.util.TypeUtils;

/**
 * Semi-synthetic node that indicates loading of a StackDumpExpression from an
 * index onto the stack.
 * 
 * @author Bibl
 */
public class StackLoadExpression extends Expression {

	private int index;
	private Type type;
	private boolean stackVariable;
	
	public StackLoadExpression(int index, Type type, boolean stackVariable) {
		this.index = index;
		this.type = type;
		this.stackVariable = stackVariable;
	}
	
	public StackLoadExpression(int index, Type type) {
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
		return new StackLoadExpression(index, type, stackVariable);
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
			
			if(stmt instanceof StackDumpStatement) {
				if(((StackDumpStatement) stmt).getIndex() == index) {
					return true;
				}
			}
		}
		return false;
	}
}