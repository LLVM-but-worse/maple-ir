package org.mapleir.deob.interproc.builder;

import java.util.Set;

import org.objectweb.asm.tree.MethodNode;

public class MultiSiteGraphNode extends CallGraphNode {
	
	private final Set<MethodNode> methods;
	
	public MultiSiteGraphNode(Set<MethodNode> methods, int id) {
		super(id);
		this.methods = methods;
	}
	
	@Override
	public String toString() {
		return methods.toString();
	}
}