package org.mapleir.deob.interproc;

import org.mapleir.ir.code.Expr;
import org.objectweb.asm.tree.MethodNode;

public interface ChildVisitor {
	
	default void preVisitMethod(IPConstAnalysis analysis, MethodNode m) {}
	
	default void postVisitMethod(IPConstAnalysis analysis, MethodNode m) {}
	
	default void preProcessedInvocation(IPConstAnalysis analysis, MethodNode caller, MethodNode callee, Expr e) {}
	
	default void postProcessedInvocation(IPConstAnalysis analysis, MethodNode caller, MethodNode callee, Expr e) {}
}
