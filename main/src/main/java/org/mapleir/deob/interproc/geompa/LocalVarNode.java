package org.mapleir.deob.interproc.geompa;

import java.util.HashMap;
import java.util.Map;

import org.mapleir.ir.TypeCone;
import org.objectweb.asm.tree.MethodNode;

public class LocalVarNode extends VarNode {

	protected Map<Object, ContextVarNode> cvns;
	protected MethodNode method;

	LocalVarNode(PAG pag, Object variable, TypeCone tc, MethodNode m) {
		super(pag, variable, tc);
		method = m;
		// if( m == null ) throw new RuntimeException( "method shouldn't be null" );
	}

	public ContextVarNode context(Object context) {
		return cvns == null ? null : cvns.get(context);
	}

	public MethodNode getMethod() {
		return method;
	}

	/** Registers a cvn as having this node as its base. */
	void addContext(ContextVarNode cvn, Object context) {
		if (cvns == null)
			cvns = new HashMap<>();
		cvns.put(context, cvn);
	}

	@Override
	public String toString() {
		return "LocalVarNode " + getNumber() + " " + variable + " " + method;
	}
}