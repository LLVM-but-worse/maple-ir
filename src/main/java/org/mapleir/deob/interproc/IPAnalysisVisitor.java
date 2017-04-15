package org.mapleir.deob.interproc;

import org.mapleir.ir.code.expr.invoke.Invocation;
import org.objectweb.asm.tree.MethodNode;

public interface IPAnalysisVisitor {
	
	default void preVisitMethod(IPAnalysis analysis, MethodNode m) {}
	
	default void postVisitMethod(IPAnalysis analysis, MethodNode m) {}
	
	default void preProcessedInvocation(IPAnalysis analysis, MethodNode caller, MethodNode callee, Invocation e) {}
	
	default void postProcessedInvocation(IPAnalysis analysis, MethodNode caller, MethodNode callee, Invocation e) {}
}
