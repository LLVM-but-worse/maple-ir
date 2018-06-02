package org.mapleir.ir.code.expr.invoke;

import org.mapleir.ir.code.Expr;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class StaticInvocationExpr extends InvocationExpr {
	public StaticInvocationExpr(Expr[] args, String owner, String name, String desc) {
		super(CallType.STATIC, args, owner, name, desc);
	}

	@Override
	public StaticInvocationExpr copy() {
		return new StaticInvocationExpr(copyArgs(), getOwner(), getName(), getDesc());
	}

	@Override
	public boolean isStatic() {
		return true;
	}

	@Override
	protected void generateCallCode(MethodVisitor visitor) {
		visitor.visitMethodInsn(Opcodes.INVOKESTATIC, getOwner(), getName(), getDesc(), getCallType() == CallType.INTERFACE);
	}
}
