package org.mapleir.deob.interproc;

import org.mapleir.context.AnalysisContext;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.invoke.InitialisedObjectExpr;
import org.mapleir.ir.code.expr.invoke.InvocationExpr;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

import java.util.Set;

public class IRCallTracer extends CallTracer {

	protected final AnalysisContext context;
	
	public IRCallTracer(AnalysisContext context) {
		super(context.getApplication(), context.getInvocationResolver());
		this.context = context;
	}

	@Override
	protected void traceImpl(MethodNode m) {
		ControlFlowGraph cfg = context.getIRCache().getFor(m);
		if(cfg == null) {
			throw new UnsupportedOperationException("No cfg for " + m + " [" + m.instructions.size() + "]");
		}
		for(BasicBlock b : cfg.vertices()) {
			for(Stmt stmt : b) {
				for(Expr c : stmt.enumerateOnlyChildren()) {
					if(c.getOpcode() == Opcode.INVOKE) {
						InvocationExpr invoke = (InvocationExpr) c;
						
						boolean isStatic = (invoke.getCallType() == Opcodes.INVOKESTATIC);
						String owner = invoke.getOwner();
						String name = invoke.getName();
						String desc = invoke.getDesc();
						
						if(isStatic) {
							MethodNode call = resolver.resolveStaticCall(owner, name, desc);
							if(call != null) {
								trace(call);
								processedInvocation(m, call, invoke);
							}
						} else {
							if(name.equals("<init>")) {
								MethodNode call = resolver.resolveVirtualInitCall(owner, desc);
								if(call != null) {
									trace(call);
									processedInvocation(m, call, invoke);
								} else {
									System.err.printf("(warn): can't resolve constructor: %s.<init> %s.%n", owner, desc);
								}
							} else {
//								if(owner.equals("java/lang/Object") && name.equals("equals")) {
//									System.err.println(cfg);
//									throw new RuntimeException("current context: " + m);
//								}
								Set<MethodNode> targets = resolver.resolveVirtualCalls(owner, name, desc, true);
								if(targets.size() > 0) {
									for(MethodNode vtarg : targets) {
										trace(vtarg);
										processedInvocation(m, vtarg, invoke);
									}
								} else {
									if(!owner.contains("java")) {
										System.err.printf("(warn): can't resolve vcall: %s.%s %s.%n", owner, name, desc);
										System.err.println("  call from " + m);
										
										System.err.println(context.getApplication().findClassNode(owner).methods);
									}
								}
							}
						}
					} else if(c.getOpcode() == Opcode.INIT_OBJ) {
						InitialisedObjectExpr init = (InitialisedObjectExpr) c;
						MethodNode call = resolver.resolveVirtualInitCall(init.getOwner(), init.getDesc());
						if(call != null) {
							trace(call);
							processedInvocation(m, call, init);
						} else {
							System.err.printf("(warn): can't resolve constructor: %s.<init> %s.%n", init.getOwner(), init.getDesc());
						}
					}
				}
			}
		}
	}
}