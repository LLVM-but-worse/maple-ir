package org.mapleir;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.InitialisedObjectExpr;
import org.mapleir.ir.code.expr.InvocationExpr;
import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.call.CallTracer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

public class IRCallTracer extends CallTracer {

	private final IContext context;
	
	public IRCallTracer(IContext context) {
		super(context.getClassTree(), context.getInvocationResolver());
		this.context = context;
	}

	@Override
	protected void traceImpl(MethodNode m) {
		ControlFlowGraph cfg = context.getIR(m);
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
							MethodNode call = resolver.findStaticCall(owner, name, desc);
							if(call != null) {
								trace(call);
								processedInvocation(m, call, invoke);
							}
						} else {
							for(MethodNode vtarg : resolver.resolveVirtualCalls(owner, name, desc)) {
								if(vtarg != null) {
									trace(vtarg);
									processedInvocation(m, vtarg, invoke);
								}
							}
						}
					} else if(c.getOpcode() == Opcode.INIT_OBJ) {
						InitialisedObjectExpr init = (InitialisedObjectExpr) c;
						MethodNode call = resolver.resolveVirtualInitCall(init.getOwner(), init.getDesc());
						if(call != null) {
							trace(call);
							processedInvocation(m, call, init);
						}
					}
				}
			}
		}
	}
}