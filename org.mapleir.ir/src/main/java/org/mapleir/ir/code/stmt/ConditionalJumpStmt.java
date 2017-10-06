package org.mapleir.ir.code.stmt;

import org.mapleir.ir.TypeUtils;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.CodeUnit;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.stdlib.util.TabbedStringWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.Printer;

public class ConditionalJumpStmt extends Stmt {

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

	private Expr left;
	private Expr right;
	private BasicBlock trueSuccessor;
	private ComparisonType type;

	public ConditionalJumpStmt(Expr left, Expr right, BasicBlock trueSuccessor, ComparisonType type) {
		super(COND_JUMP);
		setLeft(left);
		setRight(right);
		setTrueSuccessor(trueSuccessor);
		setType(type);
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

	public BasicBlock getTrueSuccessor() {
		return trueSuccessor;
	}

	public void setTrueSuccessor(BasicBlock trueSuccessor) {
		this.trueSuccessor = trueSuccessor;
	}

	public ComparisonType getComparisonType() {
		return type;
	}

	public void setType(ComparisonType type) {
		this.type = type;
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
	public void toString(TabbedStringWriter printer) {
		printer.print("if ");
		printer.print('(');
		left.toString(printer);
		printer.print(" " + type.getSign() + " ");
		right.toString(printer);
		printer.print(')');
		printer.tab();
		printer.print("\ngoto " + trueSuccessor.getId());
		printer.untab();
	}

	@Override
	public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
		Type opType = TypeUtils.resolveBinOpType(left.getType(), right.getType());

		if (TypeUtils.isObjectRef(opType)) {
			boolean isNull = right instanceof ConstantExpr && ((ConstantExpr) right).getConstant() == null;
			if (type != ComparisonType.EQ && type != ComparisonType.NE) {
				throw new IllegalArgumentException(type.toString());
			}

			left.toCode(visitor, cfg);
			if (isNull) {
				visitor.visitJumpInsn(type == ComparisonType.EQ ? Opcodes.IFNULL : Opcodes.IFNONNULL, trueSuccessor.getLabel());
			} else {
				right.toCode(visitor, cfg);
				visitor.visitJumpInsn(type == ComparisonType.EQ ? Opcodes.IF_ACMPEQ : Opcodes.IF_ACMPNE, trueSuccessor.getLabel());
			}
		} else if (opType == Type.INT_TYPE) {
			boolean canShorten = right instanceof ConstantExpr && ((ConstantExpr) right).getConstant() instanceof Number
					&& ((Number) ((ConstantExpr) right).getConstant()).intValue() == 0;

			left.toCode(visitor, cfg);
			int[] cast = TypeUtils.getPrimitiveCastOpcodes(left.getType(), opType);
			for (int i = 0; i < cast.length; i++) {
				visitor.visitInsn(cast[i]);
			}
			if (canShorten) {
				visitor.visitJumpInsn(Opcodes.IFEQ + type.ordinal(), trueSuccessor.getLabel());
			} else {
				right.toCode(visitor, cfg);
				cast = TypeUtils.getPrimitiveCastOpcodes(right.getType(), opType);
				for (int i = 0; i < cast.length; i++) {
					visitor.visitInsn(cast[i]);
				}
				visitor.visitJumpInsn(Opcodes.IF_ICMPEQ + type.ordinal(), trueSuccessor.getLabel());
			}
		} else if (opType == Type.LONG_TYPE) {
			left.toCode(visitor, cfg);
			int[] cast = TypeUtils.getPrimitiveCastOpcodes(left.getType(), opType);
			for (int i = 0; i < cast.length; i++) {
				visitor.visitInsn(cast[i]);
			}
			right.toCode(visitor, cfg);
			cast = TypeUtils.getPrimitiveCastOpcodes(right.getType(), opType);
			for (int i = 0; i < cast.length; i++) {
				visitor.visitInsn(cast[i]);
			}
			visitor.visitInsn(Opcodes.LCMP);
			visitor.visitJumpInsn(Opcodes.IFEQ + type.ordinal(), trueSuccessor.getLabel());
		} else if (opType == Type.FLOAT_TYPE) {
			left.toCode(visitor, cfg);
			int[] cast = TypeUtils.getPrimitiveCastOpcodes(left.getType(), opType);
			for (int i = 0; i < cast.length; i++) {
				visitor.visitInsn(cast[i]);
			}
			right.toCode(visitor, cfg);
			cast = TypeUtils.getPrimitiveCastOpcodes(right.getType(), opType);
			for (int i = 0; i < cast.length; i++) {
				visitor.visitInsn(cast[i]);
			}
			visitor.visitInsn((type == ComparisonType.LT || type == ComparisonType.LE) ? Opcodes.FCMPL : Opcodes.FCMPG);
			visitor.visitJumpInsn(Opcodes.IFEQ + type.ordinal(), trueSuccessor.getLabel());
		} else if (opType == Type.DOUBLE_TYPE) {
			left.toCode(visitor, cfg);
			int[] cast = TypeUtils.getPrimitiveCastOpcodes(left.getType(), opType);
			for (int i = 0; i < cast.length; i++) {
				visitor.visitInsn(cast[i]);
			}
			right.toCode(visitor, cfg);
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
	public ConditionalJumpStmt copy() {
		return new ConditionalJumpStmt(left.copy(), right.copy(), trueSuccessor, type);
	}

	@Override
	public boolean equivalent(CodeUnit s) {
		if(s instanceof ConditionalJumpStmt) {
			ConditionalJumpStmt jump = (ConditionalJumpStmt) s;
			return type == jump.type && left.equivalent(jump.left) && right.equals(jump.right) && trueSuccessor.getNumericId() == jump.trueSuccessor.getNumericId();
		}
		return false;
	}
}