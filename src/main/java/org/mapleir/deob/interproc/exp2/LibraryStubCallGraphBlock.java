package org.mapleir.deob.interproc.exp2;

import org.objectweb.asm.tree.MethodNode;

public class LibraryStubCallGraphBlock extends CallGraphBlock {
	public final MethodNode method;
	
	public LibraryStubCallGraphBlock(MethodNode method, int id) {
		super(id);
		this.method = method;
	}
}