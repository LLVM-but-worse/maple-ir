package org.mapleir.ir.code.expr;

import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.mapleir.stdlib.util.TypeUtils;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.Printer;

import static org.objectweb.asm.Opcodes.DREM;
import static org.objectweb.asm.Opcodes.IADD;
import static org.objectweb.asm.Opcodes.IAND;
import static org.objectweb.asm.Opcodes.IOR;
import static org.objectweb.asm.Opcodes.ISHL;
import static org.objectweb.asm.Opcodes.IXOR;
import static org.objectweb.asm.Opcodes.LAND;
import static org.objectweb.asm.Opcodes.LOR;
import static org.objectweb.asm.Opcodes.LUSHR;
import static org.objectweb.asm.Opcodes.LXOR;

public class ArithmeticExpr extends Expr {

	public enum Operator {
		// TODO: verify bitwise order
		ADD("+", false), SUB("-", true), MUL("*", false), DIV("/", true), REM("%", true), SHL("<<", true), SHR(">>", true), USHR(">>>", true), OR("|", true), AND("&", true), XOR("^", true);
		private final String sign;
		private final boolean order;

		private Operator(String sign, boolean order) {
			this.sign = sign;
			this.order = order;
		}

		public String getSign() {
			return sign;
		}
		
		public boolean doesOrderMatter() {
			return order;
		}
		
		public static Operator resolve(int bOpcode) {
			if(bOpcode >= IADD && bOpcode <= DREM){
				return values()[(int)Math.floor((bOpcode - IADD) / 4) + Operator.ADD.ordinal()];
			} else if(bOpcode >= ISHL && bOpcode <= LUSHR) {
				return values()[(int)Math.floor((bOpcode - ISHL) / 2) + Operator.SHL.ordinal()];
			} else if(bOpcode == IAND || bOpcode == LAND) {
				return Operator.AND;
			} else if(bOpcode == IOR || bOpcode == LOR) {
				return Operator.OR;
			} else if(bOpcode == IXOR || bOpcode == LXOR) {
				return Operator.XOR;
			} else {
				throw new UnsupportedOperationException(Printer.OPCODES[bOpcode]);
			}
		}
	}

	private Expr right;
	private Expr left;
	private Operator operator;

	public ArithmeticExpr(Expr right, Expr left, Operator operator) {
		super(ARITHMETIC);
		setLeft(left);
		setRight(right);
		this.operator = operator;
	}

	public Expr getLeft() {
		return left;
	}

	public void setLeft(Expr left) {
		this.left = left;
		overwrite(left, 0);
	}

	public Expr getRight() {
		return right;
	}

	public void setRight(Expr right) {
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
	public Expr copy() {
		return new ArithmeticExpr(right.copy(), left.copy(), operator);
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
			setLeft(read(ptr));
		} else if (ptr == 1) {
			setRight(read(ptr));
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
		printer.print("{");
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
		printer.print("}");
	}

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		Type leftType = null;
		Type rightType = null;
		if (operator == Operator.SHL || operator == Operator.SHR || operator == Operator.USHR) {
			leftType = getType();
			rightType = Type.INT_TYPE;
		} else {
			leftType = rightType = getType();
		}
		left.toCode(visitor, cfg);
		int[] lCast = TypeUtils.getPrimitiveCastOpcodes(left.getType(), leftType);
		for (int i = 0; i < lCast.length; i++)
			visitor.visitInsn(lCast[i]);

		right.toCode(visitor, cfg);
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
	public boolean isAffectedBy(CodeUnit stmt) {
		return left.isAffectedBy(stmt) || right.isAffectedBy(stmt);
	}

	@Override
	public boolean equivalent(CodeUnit s) {
		if(s instanceof ArithmeticExpr) {
			ArithmeticExpr arith = (ArithmeticExpr) s;
			return arith.operator == operator && left.equivalent(arith.left) && right.equivalent(arith.right);
		}
		return false;
	}

//	@Override
//	public int getExecutionCost() {
//		// TODO Auto-generated method stub
//		return 0;
//	}
}