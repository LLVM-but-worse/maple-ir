package org.mapleir.deob.interproc;

import org.mapleir.ir.code.Expr;
import org.objectweb.asm.tree.MethodNode;

public interface IPAnalysisVisitor {
	
	default void preVisitMethod(IPAnalysis analysis, MethodNode m) {}
	
	default void postVisitMethod(IPAnalysis analysis, MethodNode m) {}
	
	default void preProcessedInvocation(IPAnalysis analysis, MethodNode caller, MethodNode callee, Expr e) {}
	
	default void postProcessedInvocation(IPAnalysis analysis, MethodNode caller, MethodNode callee, Expr e) {}
}
