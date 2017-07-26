package org.mapleir.deob.passes;

import java.util.List;

import org.mapleir.context.AnalysisContext;
import org.mapleir.context.InvocationResolver;
import org.mapleir.deob.IPass;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.invoke.InvocationExpr;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class ConcreteStaticInvocationPass implements IPass {

	@Override
	public boolean isQuantisedPass() {
		return false;
	}
	
	@Override
	public int accept(AnalysisContext cxt, IPass prev, List<IPass> completed) {
		int fixed = 0;
		
		InvocationResolver resolver = cxt.getInvocationResolver();
		
		for(ClassNode cn : cxt.getApplication().iterate()) {
			for(MethodNode mn : cn.methods) {
				ControlFlowGraph cfg = cxt.getIRCache().getFor(mn);
				
				for(BasicBlock b : cfg.vertices()) {
					for(Stmt stmt : b) {
						for(Expr e : stmt.enumerateOnlyChildren()) {
							if(e.getOpcode() == Opcode.INVOKE) {
								InvocationExpr invoke = (InvocationExpr) e;
								
								if(invoke.isStatic()) {
									MethodNode invoked = resolver.resolveStaticCall(invoke.getOwner(), invoke.getName(), invoke.getDesc());
									
									if(invoked != null) {
										if(!invoked.owner.name.equals(invoke.getOwner())) {
											invoke.setOwner(invoked.owner.name);
											fixed++;
										}
									}
								}
							}
						}
					}
				}
			}
		}
		
		System.out.printf("  corrected %d dodgy static calls.%n", fixed);
		
		return fixed;
	}
}