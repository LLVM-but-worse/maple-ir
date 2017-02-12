package org.mapleir.deobimpl2;

import java.util.List;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.InvocationExpression;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.deob.ICompilerPass;
import org.mapleir.stdlib.klass.InvocationResolver;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class ConcreteStaticInvocationPass implements ICompilerPass {

	@Override
	public void accept(IContext cxt, ICompilerPass prev, List<ICompilerPass> completed) {
		int fixed = 0;
		
		InvocationResolver resolver = cxt.getInvocationResolver();
		
		for(ClassNode cn : cxt.getClassTree().getClasses().values()) {
			for(MethodNode mn : cn.methods) {
				ControlFlowGraph cfg = cxt.getIR(mn);
				
				for(BasicBlock b : cfg.vertices()) {
					for(Stmt stmt : b) {
						for(Expr e : stmt.enumerateOnlyChildren()) {
							if(e.getOpcode() == Opcode.INVOKE) {
								InvocationExpression invoke = (InvocationExpression) e;
								
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
	}
}