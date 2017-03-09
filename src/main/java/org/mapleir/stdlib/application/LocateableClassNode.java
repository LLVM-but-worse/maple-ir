package org.mapleir.stdlib.application;

import org.objectweb.asm.tree.ClassNode;

public class LocateableClassNode {

	public final ClassSource source;
	public final ClassNode node;
	
	public LocateableClassNode(ClassSource source, ClassNode node) {
		this.source = source;
		this.node = node;
	}
	
	@Override
	public String toString() {
		return node.name + " from " + source;
	}
}