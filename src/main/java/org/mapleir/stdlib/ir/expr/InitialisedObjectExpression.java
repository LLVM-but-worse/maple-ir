package org.mapleir.stdlib.ir.expr;

import org.mapleir.stdlib.cfg.util.TabbedStringWriter;
import org.mapleir.stdlib.cfg.util.TypeUtils;
import org.mapleir.stdlib.ir.stat.Statement;
import org.mapleir.stdlib.ir.transform.impl.CodeAnalytics;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class InitialisedObjectExpression extends Expression {

	private Type type;
	private String owner;
	private String desc;
	private Expression[] argumentExpressions;
	
	public InitialisedObjectExpression(Type type, String owner, String desc, Expression[] argumentExpressions) {
		this.type = type;
		this.owner = owner;
		this.desc = desc;
		this.argumentExpressions = argumentExpressions;
		for (int i = 0; i < argumentExpressions.length; i++) {
			overwrite(argumentExpressions[i], i);
		}
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public String getDesc() {
		return desc;
	}

	public void setDesc(String desc) {
		this.desc = desc;
	}

	public Expression[] getArgumentExpressions() {
		return argumentExpressions;
	}

	public void setArguments(Expression[] argumentExpressions) {
		this.argumentExpressions = argumentExpressions;
	}

	public void setType(Type type) {
		this.type = type;
	}

	@Override
	public Type getType() {
		return type;
	}
	
	@Override
	public Precedence getPrecedence0() {
		return Precedence.METHOD_INVOCATION;
	}

	public void updateArgument(int id, Expression argument) {
		if (id < 0 || (id) >= argumentExpressions.length) {
			throw new ArrayIndexOutOfBoundsException();
		}
		
		argumentExpressions[id] = argument;
		overwrite(argument, id);
	}
	
	@Override
	public void onChildUpdated(int ptr) {
		updateArgument(ptr, (Expression)read(ptr));
	}

	@Override
	public Expression copy() {
		Expression[] args = new Expression[argumentExpressions.length];
		for(int i=0; i < argumentExpressions.length; i++) {
			args[i] = argumentExpressions[i].copy();
		}
		return new InitialisedObjectExpression(type, owner, desc, args);
	}

	@Override
	public void toString(TabbedStringWriter printer) {
		printer.print("new ");
		printer.print(type.getInternalName().replace('/', '.'));
		printer.print('(');
		for (int i = 0; i < argumentExpressions.length; i++) {
			boolean needsComma = (i + 1) < argumentExpressions.length;
			argumentExpressions[i].toString(printer);
			if (needsComma)
				printer.print(", ");
		}
		printer.print(')');
	}

	@Override
	public void toCode(MethodVisitor visitor, CodeAnalytics analytics) {
		Type[] argTypes = Type.getArgumentTypes(desc);
		if (argTypes.length < argumentExpressions.length) {
			Type[] bck = argTypes;
			argTypes = new Type[bck.length + 1];
			System.arraycopy(bck, 0, argTypes, 1, bck.length);
			argTypes[0] = Type.getType("L" + owner + ";");
		}
		
		visitor.visitTypeInsn(Opcodes.NEW, type.getInternalName());
		visitor.visitInsn(Opcodes.DUP);
		for (int i = 0; i < argumentExpressions.length; i++) {
			argumentExpressions[i].toCode(visitor, analytics);
			if (TypeUtils.isPrimitive(argumentExpressions[i].getType())) {
				int[] cast = TypeUtils.getPrimitiveCastOpcodes(argumentExpressions[i].getType(), argTypes[i]);
				for (int a = 0; a < cast.length; a++)
					visitor.visitInsn(cast[a]);
			}
		}
		visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", desc, false);		
	}

	@Override
	public boolean canChangeFlow() {
		return false;
	}

	@Override
	public boolean canChangeLogic() {
		return true;
	}

	@Override
	public boolean isAffectedBy(Statement stmt) {
		if(stmt.canChangeLogic()) {
			return true;
		}
		
		for(Expression e : argumentExpressions) {
			if(e.isAffectedBy(stmt)) {
				return true;
			}
		}
		
		return false;
	}

	@Override
	public boolean equivalent(Statement s) {
		if(s instanceof InitialisedObjectExpression) {
			InitialisedObjectExpression o = (InitialisedObjectExpression) s;
			if(!type.equals(o.type) || !owner.equals(o.owner) || !desc.equals(o.desc)) {
				return false;
			}
			if(argumentExpressions.length != o.argumentExpressions.length) {
				return false;
			}
			for(int i=0; i < argumentExpressions.length; i++) {
				if(!argumentExpressions[i].equivalent(o.argumentExpressions[i])) {
					return false;
				}
			}
			return true;
		}
		return false;
	}
}