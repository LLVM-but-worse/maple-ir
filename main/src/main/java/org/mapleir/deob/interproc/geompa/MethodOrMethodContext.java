package org.mapleir.deob.interproc.geompa;

import org.objectweb.asm.tree.MethodNode;

public interface MethodOrMethodContext {
	public MethodNode method();

	public Context context();
}