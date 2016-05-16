package org.rsdeob.stdlib.cfg.expr;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.rsdeob.stdlib.cfg.stat.Statement;
import org.rsdeob.stdlib.cfg.util.TabbedStringWriter;
import org.rsdeob.stdlib.cfg.util.TypeUtils;

import com.sun.xml.internal.ws.org.objectweb.asm.Opcodes;

public class NewArrayExpression extends Expression {

	private Expression[] bounds;
	private Type type;

	public NewArrayExpression(Expression[] bounds, Type type) {
		this.bounds = bounds;
		for (int i = 0; i < bounds.length; i++) {
			overwrite(bounds[i], i);
		}
		this.type = type;
	}

	public Expression[] getBounds() {
		return bounds;
	}

	public void setBounds(Expression[] bounds) {
		if (type.getDimensions() < bounds.length || bounds.length <= 0)
			throw new ArrayIndexOutOfBoundsException();

		if (bounds.length < this.bounds.length) {
			setChildPointer(0);
			while (read(0) != null) {
				delete();
			}
		}

		this.bounds = bounds;
		for (int i = 0; i < bounds.length; i++) {
			overwrite(bounds[i], i);
		}
	}

	public void updateLength(int dimension, Expression length) {
		if (dimension < 0 || dimension >= bounds.length)
			throw new ArrayIndexOutOfBoundsException();

		bounds[dimension] = length;
		overwrite(length, dimension);
	}

	@Override
	public Expression copy() {
		Expression[] bounds = new Expression[this.bounds.length];
		for (int i = 0; i < bounds.length; i++)
			bounds[i] = this.bounds[i].copy();
		return new NewArrayExpression(bounds, type);
	}

	@Override
	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	@Override
	public void onChildUpdated(int ptr) {
		updateLength(ptr, (Expression) read(ptr));
	}
	
	@Override
	public Precedence getPrecedence0() {
		return Precedence.ARRAY_ACCESS;
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print("new " + type.getElementType().getClassName());
		for (int dim = 0; dim < type.getDimensions(); dim++) {
			printer.print('[');
			if (dim < bounds.length) {
				bounds[dim].toString(printer);
			}
			printer.print(']');
		}
	}

	@Override
	public void toCode(MethodVisitor visitor) {
		for (int i = 0; i < bounds.length; i++) {
			bounds[i].toCode(visitor);
			int[] cast = TypeUtils.getPrimitiveCastOpcodes(bounds[i].getType(), Type.INT_TYPE);
			for (int a = 0; a < cast.length; a++)
				visitor.visitInsn(cast[a]);
		}

		if (type.getDimensions() != 1) {
			visitor.visitMultiANewArrayInsn(type.getDescriptor(), bounds.length);
		} else {
			Type element = type.getElementType();
			if (element.getSort() == Type.OBJECT || element.getSort() == Type.METHOD) {
				visitor.visitTypeInsn(Opcodes.ANEWARRAY, element.getInternalName());
			} else {
				visitor.visitIntInsn(Opcodes.NEWARRAY, TypeUtils.getPrimitiveArrayOpcode(type));
			}
		}
	}

	@Override
	public boolean canChangeFlow() {
		return false;
	}

	@Override
	public boolean canChangeLogic() {
		for(Expression e : bounds) {
			if(e.canChangeLogic()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		for(Expression e : bounds) {
			if(e.isAffectedBy(stmt)) {
				return true;
			}
		}
		return false;
	}
}