package org.mapleir.deob.interproc.sensitive;

import org.mapleir.ir.code.Expr;
import org.mapleir.stdlib.collections.TaintableSet;
import org.objectweb.asm.tree.MethodNode;

public class CallContext {

	private final MethodNode caller;
	private final MethodNode callee;
	private final Expr callExpr;
	private final TaintableSet<ArgumentFact> arguments;
	
	public CallContext(MethodNode caller, MethodNode callee, Expr callExpr, TaintableSet<ArgumentFact> arguments) {
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
		
		CallContext that = (CallContext) o;
		
		if (!caller.equals(that.caller))
			return false;
		if (!callee.equals(that.callee))
			return false;
		if (!callExpr.equals(that.callExpr))
			return false;
		return arguments.equals(that.arguments);
	}
	
	@Override
	public int hashCode() {
		int result = caller.hashCode();
		result = 31 * result + callee.hashCode();
		result = 31 * result + callExpr.hashCode();
		result = 31 * result + arguments.hashCode();
		return result;
	}
}