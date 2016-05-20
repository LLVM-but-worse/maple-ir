package org.rsdeob.stdlib.cfg.stat;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.expr.Expression;
import org.rsdeob.stdlib.cfg.expr.StackLoadExpression;
import org.rsdeob.stdlib.cfg.stat.base.IStackDumpNode;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.cfg.util.TypeUtils;

public class StackDumpStatement extends Statement implements IStackDumpNode {

	public String creation;
	private Expression expression;
	private int index;
	private Type type;
	private boolean stackVariable;
	
	public StackDumpStatement(Expression expression, int index, Type type) {
		this(expression, index, type, false);
	}

	public StackDumpStatement(Expression expression, int index, Type type, boolean stackVariable) {
		StringWriter w = new StringWriter();
		new Exception().printStackTrace(new PrintWriter(w));
		creation = w.toString();
		
		this.expression = expression;
		this.index = index;
		this.type = type;
		this.stackVariable = stackVariable;
		
		overwrite(expression, 0);
	}

	public boolean isStackVariable() {
		return stackVariable;
	}
	
	@Override
	public Expression getExpression() {
		return expression;
	}

	@Override
	public void setExpression(Expression expression) {
		this.expression = expression;
		overwrite(expression, 0);
	}

	@Override
	public int getIndex() {
		return index;
	}

	@Override
	public void setIndex(int index) {
		this.index = index;
	}

	@Override
	public Type getType() {
		return type;
	}

	@Override
	public void setType(Type type) {
		this.type = type;
	}

	@Override
	public void onChildUpdated(int ptr) {
		setExpression((Expression) read(ptr));
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print((stackVariable ? "s" : "l") + "var" + index + " = ");
		expression.toString(printer);
		printer.print(';');
	}

	@Override
	public void toCode(MethodVisitor visitor) {
		expression.toCode(visitor);
		if (TypeUtils.isPrimitive(type)) {
			int[] cast = TypeUtils.getPrimitiveCastOpcodes(expression.getType(), type);
			for (int i = 0; i < cast.length; i++)
				visitor.visitInsn(cast[i]);
		}
		visitor.visitVarInsn(TypeUtils.getVariableStoreOpcode(type), index);
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
		if(expression instanceof StackLoadExpression) {
			return ((StackLoadExpression) expression).getIndex() == index;
		} else {
			return false;
		}
	}
}