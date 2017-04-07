package org.mapleir.deob.interproc.sensitive;

import java.util.Arrays;

import org.mapleir.ir.code.Expr;
import org.objectweb.asm.tree.MethodNode;

public class CallContext {

	private final MethodNode caller;
	private final MethodNode callee;
	private final Expr callExpr;
	private final ArgumentFact[] arguments;
	
	public CallContext(MethodNode caller, MethodNode callee, Expr callExpr, ArgumentFact[] arguments) {
		this.caller = caller;
		this.callee = callee;
		this.callExpr = callExpr;
		this.arguments = arguments;
	}

	@Override
	public int hashCode() {
		final int prime = 37;
		int result = 1;
		result = prime * result + Arrays.hashCode(arguments);
		result = prime * result + ((callee == null) ? 0 : callee.hashCode());
		result = prime * result + ((caller == null) ? 0 : caller.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CallContext other = (CallContext) obj;
		if (!Arrays.equals(arguments, other.arguments))
			return false;
		if (callee == null) {
			if (other.callee != null)
				return false;
		} else if (!callee.equals(other.callee))
			return false;
		if (caller == null) {
			if (other.caller != null)
				return false;
		} else if (!caller.equals(other.caller))
			return false;
		return true;
	}
}