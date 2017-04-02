package org.mapleir.deobimpl2;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.InvocationExpr;
import org.mapleir.deobimpl2.cxt.IContext;
import org.mapleir.stdlib.deob.IPass;
import org.mapleir.stdlib.klass.InvocationResolver;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public class ConcreteStaticInvocationPass implements IPass {

	@Override
	public boolean isQuantisedPass() {
		return false;
	}
	
	@Override
	public int accept(IContext cxt, IPass prev, List<IPass> completed) {
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
								
								if(invoke.getInstanceExpression() == null) {
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