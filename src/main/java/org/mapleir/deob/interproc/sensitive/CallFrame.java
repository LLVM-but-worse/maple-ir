package org.mapleir.deob.interproc.sensitive;

import java.util.Set;

import org.mapleir.ir.code.expr.invoke.Invocation;
import org.objectweb.asm.tree.MethodNode;

public class CallFrame {

	public final MethodNode caller;
	public final MethodNode callee;
	public final Invocation callExpr;
	public final Set<ArgumentFact> arguments;
	
	public CallFrame(MethodNode caller, MethodNode callee, Invocation callExpr, Set<ArgumentFact> arguments) {
		this.caller = caller;
		this.callee = callee;
		this.callExpr = callExpr;
		this.arguments = arguments;
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		
		CallFrame that = (CallFrame) o;
		
		if (!caller.equals(that.caller))
			return false;
		if (!callee.equals(that.callee))
			return false;
		return arguments.equals(that.arguments);
	}
	
	@Override
	public int hashCode() {
		int result = caller.hashCode();
		result = 31 * result + callee.hashCode();
		result = 31 * result + arguments.hashCode();
		return result;
	}
}