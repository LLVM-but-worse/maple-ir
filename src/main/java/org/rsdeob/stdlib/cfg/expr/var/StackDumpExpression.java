package org.rsdeob.stdlib.cfg.expr.var;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.expr.Expression;
import org.rsdeob.stdlib.cfg.expr.StackLoadExpression;
import org.rsdeob.stdlib.cfg.stat.Statement;
import org.rsdeob.stdlib.cfg.stat.base.IStackDumpNode;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.cfg.util.TypeUtils;

public class StackDumpExpression extends Expression implements IStackDumpNode {
	
	public String creation;
	private Expression expression;
	private int index;
	private Type type;

	public StackDumpExpression(Expression expression, int index, Type type) {
//		if (!(expression.getType().getSort() == Type.ARRAY && type.getSort() == Type.OBJECT) && !expression.getType().equals(type)) {
//			throw new RuntimeException(expression.getType() + "  " + type);
//		}
		StringWriter w = new StringWriter();
		w.write(this.hashCode() + "   ");
		new Exception().printStackTrace(new PrintWriter(w));
		creation = w.toString();
		
		this.expression = expression;
		this.index = index;
		this.type = type;

		overwrite(expression, 0);
	}

	@Override
	public Precedence getPrecedence0() {
		return Precedence.ASSIGNMENT;
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
	public void setType(Type type) {
		this.type = type;
	}

	@Override
	public void onChildUpdated(int ptr) {
		setExpression((Expression) read(ptr));
	}

	@Override
	public Expression copy() {
		return new StackDumpExpression(expression, index, type);
	}

	@Override
	public Type getType() {
		return type;
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		int selfPriority = getPrecedence();
		int expressionPriority = expression.getPrecedence();
		printer.print("var_" + index + " = ");
		if (expressionPriority > selfPriority)
			printer.print('(');
		expression.toString(printer);
		if (expressionPriority > selfPriority)
			printer.print(')');
	}

	@Override
	public void toCode(MethodVisitor visitor) {
//		System.err.println(expression + " " + type);
//		System.err.println();
//		System.err.println(creation);
		expression.toCode(visitor);
		if (TypeUtils.isPrimitive(type)) {
			int[] cast = TypeUtils.getPrimitiveCastOpcodes(expression.getType(), type);
			for (int i = 0; i < cast.length; i++)
				visitor.visitInsn(cast[i]);
		}
		visitor.visitInsn(TypeUtils.getDupOpcode(getType()));
		visitor.visitVarInsn(TypeUtils.getVariableStoreOpcode(getType()), index);
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