package org.mapleir.deob.interproc.geompa.cg;

import org.mapleir.ir.code.expr.invoke.Invocation;
import org.objectweb.asm.tree.MethodNode;

public interface ReflectionModel {

	void methodInvoke(MethodNode container, Invocation invokeStmt);

	void classNewInstance(MethodNode source, Invocation s);

	void contructorNewInstance(MethodNode source, Invocation s);

	void classForName(MethodNode source, Invocation s);
}