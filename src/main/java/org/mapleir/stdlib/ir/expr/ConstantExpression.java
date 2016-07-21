package org.mapleir.stdlib.ir.expr;

import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.mapleir.stdlib.ir.stat.Statement;
import org.mapleir.stdlib.ir.transform.impl.CodeAnalytics;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class ConstantExpression extends Expression {

	private Object cst;

	public ConstantExpression(Object cst) {
		this.cst = cst;
	}

	public Object getConstant() {
		return cst;
	}
	
	public void setConstant(Object o) {
		cst = o;
	}

	@Override
	public Expression copy() {
		return new ConstantExpression(cst);
	}

	@Override
	public Type getType() {
		if (cst == null) {
			return Type.getType("Ljava/lang/Object;");
		} else if (cst instanceof Integer) {
//			int val = ((Integer) cst).intValue();
//			if (val >= Byte.MIN_VALUE && val <= Byte.MAX_VALUE) {
//				return Type.BYTE_TYPE;
//			} else if (val >= Short.MIN_VALUE && val <= Short.MAX_VALUE) {
//				return Type.SHORT_TYPE;
//			} else {
//				return Type.INT_TYPE;
//			}
			return Type.INT_TYPE;
		} else if (cst instanceof Long) {
			return Type.LONG_TYPE;
		} else if (cst instanceof Float) {
			return Type.FLOAT_TYPE;
		} else if (cst instanceof Double) {
			return Type.DOUBLE_TYPE;
		} else if (cst instanceof String) {
			return Type.getType("Ljava/lang/String;");
		} else if (cst instanceof Type) {
			Type type = (Type) cst;
			if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
				return Type.getType("Ljava/lang/Class;");
			} else if (type.getSort() == Type.METHOD) {
				return Type.getType("Ljava/lang/invoke/MethodType;");
			} else {
				throw new RuntimeException("Invalid type: " + cst);
			}
		} else if (cst instanceof Handle) {
			return Type.getType("Ljava/lang/invoke/MethodHandle;");
		} else if (cst instanceof Boolean) {
			return Type.BOOLEAN_TYPE;
		} else if (cst instanceof Character) {
			return Type.CHAR_TYPE;
		} else {
			throw new RuntimeException("Invalid type: " + cst);
		}
	}

	@Override
	public void onChildUpdated(int ptr) {

	}

	@Override
	public void toString(TabbedStringWriter printer) {
		if (cst == null) {
			printer.print("null");
		} else if (cst instanceof Integer) {
			printer.print(((Integer) cst).intValue() + "");
		} else if (cst instanceof Long) {
			printer.print(((Long) cst).longValue() + "L");
		} else if (cst instanceof Float) {
			printer.print(((Float) cst).floatValue() + "F");
		} else if (cst instanceof Double) {
			printer.print(((Double) cst).doubleValue() + "D");
		} else if (cst instanceof String) {
			printer.print("\"" + cst + "\"");
		} else if (cst instanceof Type) {
			Type type = (Type) cst;
			if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
				printer.print(type.getClassName() + ".class");
			} else if (type.getSort() == Type.METHOD) {
				printer.print("methodTypeOf(" + type + ")");
			} else {
				throw new RuntimeException("WT");
			}
		} else if (cst instanceof Handle) {
			printer.print("handleOf(" + cst + ")");
		} else if (cst instanceof Boolean) {
			// synthetic values
			printer.print(((Boolean) cst).booleanValue() + "");
		} else if (cst instanceof Character) {
			// TODO , normal character printing
			printer.print('\'');
			printer.print(((Character) cst).charValue());
			printer.print('\'');
		} else {
			throw new IllegalStateException();
		}
	}

	@Override
	public void toCode(MethodVisitor visitor, CodeAnalytics analytics) {
		if (cst == null) {
			visitor.visitInsn(Opcodes.ACONST_NULL);
		} else if (cst instanceof Integer) {
			int value = ((Integer) cst).intValue();
			if (value >= -1 && value <= 5) {
				visitor.visitInsn(Opcodes.ICONST_M1 + (value + 1));
			} else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
				visitor.visitIntInsn(Opcodes.BIPUSH, value);
			} else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
				visitor.visitIntInsn(Opcodes.SIPUSH, value);
			} else {
				visitor.visitLdcInsn(value);
			}
		} else if (cst instanceof Long) {
			long value = ((Long) cst).longValue();
			if (value == 0L || value == 1L) {
				visitor.visitInsn(value == 0L ? Opcodes.LCONST_0 : Opcodes.LCONST_1);
			} else {
				visitor.visitLdcInsn(value);
			}
		} else if (cst instanceof Float) {
			float value = ((Float) cst).floatValue();
			if (value == 0F || value == 1F || value == 2F) {
				visitor.visitInsn(Opcodes.FCONST_0 + (int) value);
			} else {
				visitor.visitLdcInsn(value);
			}
		} else if (cst instanceof Double) {
			double value = ((Double) cst).doubleValue();
			if (value == 0D || value == 1D) {
				visitor.visitInsn(value == 0 ? Opcodes.DCONST_0 : Opcodes.DCONST_1);
			} else {
				visitor.visitLdcInsn(value);
			}
		} else if (cst instanceof String || cst instanceof Handle || cst instanceof Type) {
			visitor.visitLdcInsn(cst);
		}
		// synthethic values
		else if (cst instanceof Boolean) {
			boolean value = ((Boolean) cst).booleanValue();
			visitor.visitInsn(value ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
		} else if (cst instanceof Character) {
			char value = ((Character) cst).charValue();
			if (value >= -1 && value <= 5) {
				visitor.visitInsn(Opcodes.ICONST_M1 + (value + 1));
			} else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
				visitor.visitIntInsn(Opcodes.BIPUSH, value);
			} else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
				visitor.visitIntInsn(Opcodes.SIPUSH, value);
			} else {
				visitor.visitLdcInsn(value);
			}
		} else {
			throw new IllegalStateException();
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
		return false;
	}

	@Override
	public boolean equivalent(Statement s) {
		if(s instanceof ConstantExpression) {
			ConstantExpression c = (ConstantExpression) s;
			if(cst == null) {
				if(c.cst == null) {
					return true;
				} else {
					return false;
				}
			} else {
				if(c.cst == null) {
					return false;
				} else {
					return cst.equals(c.cst);
				}
			}
		}
		return false;
	}
}