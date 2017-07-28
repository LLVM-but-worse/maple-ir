package org.mapleir.deob.interproc.geompa;

import org.objectweb.asm.tree.MethodNode;

public interface MapleMethodOrMethodContext {
	public MapleMethod method();

	public Context context();
}