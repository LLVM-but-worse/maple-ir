package org.mapleir.deob.interproc;

import java.util.Set;

import org.mapleir.context.AnalysisContext;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.invoke.Invocation;
import org.objectweb.asm.tree.MethodNode;

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
		
		for(Stmt stmt : cfg.stmts()) {
			for(Expr c : stmt.enumerateOnlyChildren()) {
				if(c instanceof Invocation) {
					Invocation invoke = (Invocation) c;
					
					Set<MethodNode> targets = invoke.resolveTargets(resolver);
					
					if(targets.size() != 0) {
						for(MethodNode vtarg : targets) {
							trace(vtarg);
							processedInvocation(m, vtarg, invoke);
						}
					} else {
						String owner = invoke.getOwner(), name = invoke.getName(), desc = invoke.getDesc();
						//FIXME
						if(owner.equals("<init>")) {
							System.err.printf("(warn): can't resolve constructor: %s.<init> %s.%n", owner, desc);
						} else if(!invoke.isStatic()) {
							if(!owner.contains("java")) {
								System.err.printf("(warn): can't resolve vcall: %s.%s %s.%n", owner, name, desc);
								System.err.println("  call from " + m);
								System.err.println(context.getApplication().findClassNode(owner).methods);
							}
						}
					}
				}
			}
		}
	}
}