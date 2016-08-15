package org.mapleir.ir.code.expr;

import static org.objectweb.asm.Opcodes.*;

import org.mapleir.ir.code.stmt.Statement;
import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.mapleir.ir.analysis.dataflow.impl.CodeAnalytics;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.Printer;

public class ComparisonExpression extends Expression {

	public enum ValueComparisonType {
		LT, GT, CMP;
		
		public static ValueComparisonType resolve(int opcode) {
			if(opcode == LCMP) {
				return CMP;
			} else if(opcode == FCMPG || opcode == DCMPG) {
				return ValueComparisonType.GT;
			} else if(opcode == FCMPL || opcode == DCMPL) {
				return ValueComparisonType.LT;
			} else {
				throw new UnsupportedOperationException(Printer.OPCODES[opcode]);
			}
		}
	}

	private Expression left;
	private Expression right;
	private ValueComparisonType type;

	public ComparisonExpression(Expression left, Expression right, ValueComparisonType type) {
		super(COMPARE);
		setLeft(left);
		setRight(right);
		this.type = type;
	}

	public Expression getLeft() {
		return left;
	}

	public void setLeft(Expression left) {
		this.left = left;
		overwrite(left, 0);
	}

	public Expression getRight() {
		return right;
	}

	public void setRight(Expression right) {
		this.right = right;
		overwrite(right, 1);
	}

	public void setType(ValueComparisonType type) {
		this.type = type;
	}

	@Override
	public Expression copy() {
		return new ComparisonExpression(left.copy(), right.copy(), type);
	}

	@Override
	public Type getType() {
		return Type.INT_TYPE;
	}

	@Override
	public void onChildUpdated(int ptr) {
		if (ptr == 0) {
			setLeft((Expression) read(ptr));
		} else if (ptr == 1) {
			setRight((Expression) read(ptr));
		}
	}
	
	@Override
	public Precedence getPrecedence0() {
		return Precedence.METHOD_INVOCATION;
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print('(');
		left.toString(printer);
		printer.print(" == ");
		right.toString(printer);
		printer.print("? 0 : (");
		right.toString(printer);
		printer.print(" > ");
		left.toString(printer);
		printer.print("? 1 : -1))");
	}

	@Override
	public void toCode(MethodVisitor visitor, CodeAnalytics analytics) {
		left.toCode(visitor, analytics);
		right.toCode(visitor, analytics);

		if (left.getType() == Type.LONG_TYPE || right.getType() == Type.LONG_TYPE) {
			visitor.visitInsn(Opcodes.LCMP);
		} else if (left.getType() == Type.FLOAT_TYPE || right.getType() == Type.FLOAT_TYPE) {
			visitor.visitInsn(type == ValueComparisonType.GT ? Opcodes.FCMPG : Opcodes.FCMPL);
		} else if (left.getType() == Type.DOUBLE_TYPE || right.getType() == Type.DOUBLE_TYPE) {
			visitor.visitInsn(type == ValueComparisonType.GT ? Opcodes.DCMPG : Opcodes.DCMPL);
		} else {
			throw new IllegalArgumentException();
		}
	}

	@Override
	public boolean canChangeFlow() {
		return false;
	}

	@Override
	public boolean canChangeLogic() {
		return left.canChangeLogic() || right.canChangeLogic();
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		return left.isAffectedBy(stmt) || right.isAffectedBy(stmt);
	}

	public ValueComparisonType getComparisonType() {
		return type;
	}

	@Override
	public boolean equivalent(Statement s) {
		if(s instanceof ComparisonExpression) {
			ComparisonExpression comp = (ComparisonExpression) s;
			return type == comp.type && left.equivalent(comp.left) && right.equals(comp.right);
		}
		return false;
	}
}