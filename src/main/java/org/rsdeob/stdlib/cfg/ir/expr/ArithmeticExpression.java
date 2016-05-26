package org.rsdeob.stdlib.cfg.ir.expr;

import static org.objectweb.asm.Opcodes.*;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.Printer;
import org.rsdeob.stdlib.cfg.ir.stat.Statement;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.cfg.util.TypeUtils;

public class ArithmeticExpression extends Expression {

	public enum Operator {
		ADD("+"), SUB("-"), MUL("*"), DIV("/"), REM("%"), SHL("<<"), SHR(">>"), USHR(">>>"), OR("|"), AND("&"), XOR("^");
		private final String sign;

		private Operator(String sign) {
			this.sign = sign;
		}

		public String getSign() {
			return sign;
		}
		
		public static Operator resolve(int opcode) {
			if(opcode >= IADD && opcode <= DREM){
				return values()[(int)Math.floor((opcode - IADD) / 4) + Operator.ADD.ordinal()];
			} else if(opcode >= ISHL && opcode <= LUSHR) {
				return values()[(int)Math.floor((opcode - ISHL) / 2) + Operator.SHL.ordinal()];
			} else if(opcode == IAND || opcode == LAND) {
				return Operator.AND;
			} else if(opcode == IOR || opcode == LOR) {
				return Operator.OR;
			} else if(opcode == IXOR || opcode == LXOR) {
				return Operator.XOR;
			} else {
				throw new UnsupportedOperationException(Printer.OPCODES[opcode]);
			}
		}
	}

	private Expression right;
	private Expression left;
	private Operator operator;

	public ArithmeticExpression(Expression right, Expression left, Operator operator) {
		setLeft(left);
		setRight(right);
		this.operator = operator;
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

	public Operator getOperator() {
		return operator;
	}

	public void setOperator(Operator operator) {
		this.operator = operator;
	}

	@Override
	public Expression copy() {
		return new ArithmeticExpression(left, right, operator);
	}

	@Override
	public Type getType() {
		if (operator == Operator.SHL || operator == Operator.SHR) {
			return TypeUtils.resolveUnaryOpType(left.getType());
		} else {
			return TypeUtils.resolveBinOpType(left.getType(), right.getType());
		}
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
		switch (operator) {
			case ADD:
			case SUB:
				return Precedence.ADD_SUB;
			case MUL:
			case DIV:
			case REM:
				return Precedence.MUL_DIV_REM;
			case SHL:
			case SHR:
			case USHR:
				return Precedence.BITSHIFT;
			case OR:
				return Precedence.BIT_OR;
			case AND:
				return Precedence.BIT_AND;
			case XOR:
				return Precedence.BIT_XOR;
			default:
				return super.getPrecedence0();
		}
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		int selfPriority = getPrecedence();
		int leftPriority = left.getPrecedence();
		int rightPriority = right.getPrecedence();
		if (leftPriority > selfPriority)
			printer.print('(');
		left.toString(printer);
		if (leftPriority > selfPriority)
			printer.print(')');
		printer.print(" " + operator.getSign() + " ");
		if (rightPriority > selfPriority)
			printer.print('(');
		right.toString(printer);
		if (rightPriority > selfPriority)
			printer.print(')');
	}

	@Override
	public void toCode(MethodVisitor visitor) {
		Type leftType = null;
		Type rightType = null;
		if (operator == Operator.SHL || operator == Operator.SHR) {
			leftType = getType();
			rightType = Type.INT_TYPE;
		} else {
			leftType = rightType = getType();
		}
		left.toCode(visitor);
		int[] lCast = TypeUtils.getPrimitiveCastOpcodes(left.getType(), leftType);
		for (int i = 0; i < lCast.length; i++)
			visitor.visitInsn(lCast[i]);

		right.toCode(visitor);
		int[] rCast = TypeUtils.getPrimitiveCastOpcodes(right.getType(), rightType);
		for (int i = 0; i < rCast.length; i++)
			visitor.visitInsn(rCast[i]);
		int opcode;
		switch (operator) {
			case ADD:
				opcode = TypeUtils.getAddOpcode(getType());
				break;
			case SUB:
				opcode = TypeUtils.getSubtractOpcode(getType());
				break;
			case MUL:
				opcode = TypeUtils.getMultiplyOpcode(getType());
				break;
			case DIV:
				opcode = TypeUtils.getDivideOpcode(getType());
				break;
			case REM:
				opcode = TypeUtils.getRemainderOpcode(getType());
				break;
			case SHL:
				opcode = TypeUtils.getBitShiftLeftOpcode(getType());
				break;
			case SHR:
				opcode = TypeUtils.bitShiftRightOpcode(getType());
				break;
			case USHR:
				opcode = TypeUtils.getBitShiftRightUnsignedOpcode(getType());
				break;
			case OR:
				opcode = TypeUtils.getBitOrOpcode(getType());
				break;
			case AND:
				opcode = TypeUtils.getBitAndOpcode(getType());
				break;
			case XOR:
				opcode = TypeUtils.getBitXorOpcode(getType());
				break;
			default:
				throw new RuntimeException();
		}
		visitor.visitInsn(opcode);
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
}