package org.rsdeob.stdlib.cfg.ir.stat;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.Printer;
import org.rsdeob.stdlib.cfg.ir.expr.ConstantExpression;
import org.rsdeob.stdlib.cfg.ir.expr.Expression;
import org.rsdeob.stdlib.cfg.ir.stat.header.HeaderStatement;
import org.rsdeob.stdlib.cfg.ir.transform.impl.CodeAnalytics;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.cfg.util.TypeUtils;

public class ConditionalJumpStatement extends Statement {

	public enum ComparisonType {
		EQ("=="), NE("!="), LT("<"), GE(">="), GT(">"), LE("<="), ;

		private final String sign;

		private ComparisonType(String sign) {
			this.sign = sign;
		}

		public String getSign() {
			return sign;
		}

		public static ComparisonType getType(int opcode) {
			switch (opcode) {
				case Opcodes.IF_ACMPEQ:
				case Opcodes.IF_ICMPEQ:
				case Opcodes.IFEQ:
					return EQ;
				case Opcodes.IF_ACMPNE:
				case Opcodes.IF_ICMPNE:
				case Opcodes.IFNE:
					return NE;
				case Opcodes.IF_ICMPGT:
				case Opcodes.IFGT:
					return GT;
				case Opcodes.IF_ICMPGE:
				case Opcodes.IFGE:
					return GE;
				case Opcodes.IF_ICMPLT:
				case Opcodes.IFLT:
					return LT;
				case Opcodes.IF_ICMPLE:
				case Opcodes.IFLE:
					return LE;
				default:
					throw new IllegalArgumentException(Printer.OPCODES[opcode]);
			}
		}
	}

	private Expression left;
	private Expression right;
	private HeaderStatement trueSuccessor;
	private ComparisonType type;

	public ConditionalJumpStatement(Expression left, Expression right, HeaderStatement trueSuccessor, ComparisonType type) {
		setLeft(left);
		setRight(right);
		setTrueSuccessor(trueSuccessor);
		setType(type);
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

	public HeaderStatement getTrueSuccessor() {
		return trueSuccessor;
	}

	public void setTrueSuccessor(HeaderStatement trueSuccessor) {
		this.trueSuccessor = trueSuccessor;
	}

	public ComparisonType getType() {
		return type;
	}

	public void setType(ComparisonType type) {
		this.type = type;
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
	public void toString(TabbedStringWriter printer) {
		printer.print("if ");
		printer.print('(');
		left.toString(printer);
		printer.print(" " + type.getSign() + " ");
		right.toString(printer);
		printer.print(')');
		printer.tab();
		printer.print("\ngoto " + trueSuccessor.getHeaderId());
		printer.untab();
	}

	@Override
	public void toCode(MethodVisitor visitor, CodeAnalytics analytics) {
		Type opType = TypeUtils.resolveBinOpType(left.getType(), right.getType());

		if (TypeUtils.isObjectRef(opType)) {
			boolean isNull = right instanceof ConstantExpression && ((ConstantExpression) right).getConstant() == null;
			if (type != ComparisonType.EQ && type != ComparisonType.NE) {
				throw new IllegalArgumentException(type.toString());
			}

			left.toCode(visitor, analytics);
			if (isNull) {
				visitor.visitJumpInsn(type == ComparisonType.EQ ? Opcodes.IFNULL : Opcodes.IFNONNULL, trueSuccessor.getLabel());
			} else {
				right.toCode(visitor, analytics);
				visitor.visitJumpInsn(type == ComparisonType.EQ ? Opcodes.IF_ACMPEQ : Opcodes.IF_ACMPNE, trueSuccessor.getLabel());
			}
		} else if (opType == Type.INT_TYPE) {
			boolean canShorten = right instanceof ConstantExpression && ((ConstantExpression) right).getConstant() instanceof Number
					&& ((Number) ((ConstantExpression) right).getConstant()).intValue() == 0;

			left.toCode(visitor, analytics);
			int[] cast = TypeUtils.getPrimitiveCastOpcodes(left.getType(), opType);
			for (int i = 0; i < cast.length; i++) {
				visitor.visitInsn(cast[i]);
			}
			if (canShorten) {
				visitor.visitJumpInsn(Opcodes.IFEQ + type.ordinal(), trueSuccessor.getLabel());
			} else {
				right.toCode(visitor, analytics);
				cast = TypeUtils.getPrimitiveCastOpcodes(right.getType(), opType);
				for (int i = 0; i < cast.length; i++) {
					visitor.visitInsn(cast[i]);
				}
				visitor.visitJumpInsn(Opcodes.IF_ICMPEQ + type.ordinal(), trueSuccessor.getLabel());
			}
		} else if (opType == Type.LONG_TYPE) {
			left.toCode(visitor, analytics);
			int[] cast = TypeUtils.getPrimitiveCastOpcodes(left.getType(), opType);
			for (int i = 0; i < cast.length; i++) {
				visitor.visitInsn(cast[i]);
			}
			right.toCode(visitor, analytics);
			cast = TypeUtils.getPrimitiveCastOpcodes(right.getType(), opType);
			for (int i = 0; i < cast.length; i++) {
				visitor.visitInsn(cast[i]);
			}
			visitor.visitInsn(Opcodes.LCMP);
			visitor.visitJumpInsn(Opcodes.IFEQ + type.ordinal(), trueSuccessor.getLabel());
		} else if (opType == Type.FLOAT_TYPE) {
			left.toCode(visitor, analytics);
			int[] cast = TypeUtils.getPrimitiveCastOpcodes(left.getType(), opType);
			for (int i = 0; i < cast.length; i++) {
				visitor.visitInsn(cast[i]);
			}
			right.toCode(visitor, analytics);
			cast = TypeUtils.getPrimitiveCastOpcodes(right.getType(), opType);
			for (int i = 0; i < cast.length; i++) {
				visitor.visitInsn(cast[i]);
			}
			visitor.visitInsn((type == ComparisonType.LT || type == ComparisonType.LE) ? Opcodes.FCMPL : Opcodes.FCMPG);
			visitor.visitJumpInsn(Opcodes.IFEQ + type.ordinal(), trueSuccessor.getLabel());
		} else if (opType == Type.DOUBLE_TYPE) {
			left.toCode(visitor, analytics);
			int[] cast = TypeUtils.getPrimitiveCastOpcodes(left.getType(), opType);
			for (int i = 0; i < cast.length; i++) {
				visitor.visitInsn(cast[i]);
			}
			right.toCode(visitor, analytics);
			cast = TypeUtils.getPrimitiveCastOpcodes(right.getType(), opType);
			for (int i = 0; i < cast.length; i++) {
				visitor.visitInsn(cast[i]);
			}
			visitor.visitInsn((type == ComparisonType.LT || type == ComparisonType.LE) ? Opcodes.DCMPL : Opcodes.DCMPG);
			visitor.visitJumpInsn(Opcodes.IFEQ + type.ordinal(), trueSuccessor.getLabel());
		} else {
			throw new IllegalArgumentException(opType.toString());
		}
	}

	@Override
	public boolean canChangeFlow() {
		return true;
	}

	@Override
	public boolean canChangeLogic() {
		return left.canChangeLogic() || right.canChangeLogic();
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		return left.isAffectedBy(stmt) || right.isAffectedBy(stmt);
	}

	@Override
	public Statement copy() {
		return new ConditionalJumpStatement(left, right, trueSuccessor, type);
	}

	@Override
	public boolean equivalent(Statement s) {
		if(s instanceof ConditionalJumpStatement) {
			ConditionalJumpStatement jump = (ConditionalJumpStatement) s;
			return type == jump.type && left.equivalent(jump.left) && right.equals(jump.right) && trueSuccessor.equivalent(jump.trueSuccessor);
		}
		return false;
	}
}