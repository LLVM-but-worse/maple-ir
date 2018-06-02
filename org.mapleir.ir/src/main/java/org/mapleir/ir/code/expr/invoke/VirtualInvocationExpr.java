package org.mapleir.ir.code.expr.invoke;

import org.mapleir.app.service.InvocationResolver;
import org.mapleir.ir.code.Expr;
import org.mapleir.stdlib.collections.CollectionUtils;
import org.mapleir.stdlib.collections.map.SetCreator;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

import java.util.Set;

public class VirtualInvocationExpr extends InvocationExpr {
	public VirtualInvocationExpr(CallType callType, Expr[] args, String owner, String name, String desc) {
		super(callType, args, owner, name, desc);
		if (callType != CallType.INTERFACE && callType != CallType.SPECIAL && callType != CallType.VIRTUAL)
			throw new IllegalArgumentException(callType.toString());
	}

	@Override
	public VirtualInvocationExpr copy() {
		return new VirtualInvocationExpr(getCallType(), copyArgs(), getOwner(), getName(), getDesc());
	}

	@Override
	protected void generateCallCode(MethodVisitor visitor) {
		visitor.visitMethodInsn(resolveASMOpcode(getCallType()), getOwner(), getName(), getDesc(), isInterfaceCall());
	}
	
	public boolean isInterfaceCall() {
		return getCallType() == CallType.INTERFACE;
	}
	
	@Override
	public boolean isStatic() {
		return false;
	}

	@Override
	public boolean isDynamic() {
		return false;
	}

	private static int resolveASMOpcode(CallType t) {
		switch (t) {
			case SPECIAL:
				return Opcodes.INVOKESPECIAL;
			case VIRTUAL:
				return Opcodes.INVOKEVIRTUAL;
			case INTERFACE:
				return Opcodes.INVOKEINTERFACE;
			default:
				throw new IllegalArgumentException(t.toString());
		}
	}
	
	public static CallType resolveCallType(int asmOpcode) {
		switch (asmOpcode) {
			case Opcodes.INVOKEVIRTUAL:
				return CallType.VIRTUAL;
			case Opcodes.INVOKESPECIAL:
				return CallType.SPECIAL;
			case Opcodes.INVOKEINTERFACE:
				return CallType.INTERFACE;
			default:
				throw new IllegalArgumentException(String.valueOf(asmOpcode));
		}
	}
	
	@Override
	public Set<MethodNode> resolveTargets(InvocationResolver res) {
		if(getName().equals("<init>")) {
			assert (getCallType() == InvocationExpr.CallType.SPECIAL);
			return resolveSpecialInvocation(res, getOwner(), getDesc());
		} else {
			return resolveVirtualInvocation(res, getOwner(), getName(), getDesc());
		}
	}
	
	public static Set<MethodNode> resolveSpecialInvocation(InvocationResolver res, String owner, String desc) {
		return CollectionUtils.asCollection(SetCreator.getInstance(), res.resolveVirtualInitCall(owner, desc));
	}
	
	public static Set<MethodNode> resolveVirtualInvocation(InvocationResolver res, String owner, String name, String desc) {
		return res.resolveVirtualCalls(owner, name, desc, true);
	}
}
