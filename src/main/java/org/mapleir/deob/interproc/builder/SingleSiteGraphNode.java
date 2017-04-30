package org.mapleir.deob.interproc.builder;

import org.objectweb.asm.tree.MethodNode;

public class SingleSiteGraphNode extends CallGraphNode {
	
	private final MethodNode method;
	
	public SingleSiteGraphNode(MethodNode method) {
		super(method.getNumericId());
		
		this.method = method;
	}
	
	public MethodNode getMethod() {
		return method;
	}

	@Override
	public String toString() {
		return getMethod().toString();
	}
}