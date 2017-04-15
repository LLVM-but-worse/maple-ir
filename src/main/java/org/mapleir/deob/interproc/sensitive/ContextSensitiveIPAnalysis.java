package org.mapleir.deob.interproc.sensitive;

import org.mapleir.context.IContext;
import org.mapleir.deob.interproc.IRCallTracer;
import org.mapleir.deob.interproc.sensitive.ContextSensitiveIPAnalysis.CallGraph.ContextSensitiveInvocation;
import org.mapleir.deob.intraproc.eval.ExpressionEvaluator;
import org.mapleir.deob.intraproc.eval.LocalValueResolver;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.expr.InitialisedObjectExpr;
import org.mapleir.ir.code.expr.InvocationExpr;
import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

public class ContextSensitiveIPAnalysis {
	
	private final ExpressionEvaluator evaluator;
	
	public ContextSensitiveIPAnalysis(IContext cxt, ExpressionEvaluator evaluator) {
		this.evaluator = evaluator;
		
		CallgraphBuilder builder = new CallgraphBuilder(cxt);
		for(MethodNode m : cxt.getIRCache().getActiveMethods()) {
			builder.trace(m);
		}
	}
	
	private class CallgraphBuilder extends IRCallTracer implements Opcode {
		
		private final CallGraph graph;
		
		public CallgraphBuilder(IContext context) {
			super(context);
			
			graph = new CallGraph();
		}
		
		@Override
		public void processedInvocation(MethodNode caller, MethodNode callee, Expr call) {
			Expr[] params;
			
			if(call.getOpcode() == INVOKE) {
				params = ((InvocationExpr) call).getParameterArguments();
			} else if(call.getOpcode() == INIT_OBJ) {
				params = ((InitialisedObjectExpr) call).getArgumentExpressions();
			} else {
				throw new UnsupportedOperationException(String.format("%s -> %s (%s)", caller, callee, call));
			}
			
			boolean isStatic = (callee.access & Opcodes.ACC_STATIC) != 0;
			
			int paramCount = Type.getArgumentTypes(callee.desc).length;
			int off = (isStatic ? 0 : 1);
			
			ControlFlowGraph cfg = context.getIRCache().getFor(caller);
			LocalValueResolver valueResolver = new LocalValueResolver.PoolLocalValueResolver(cfg.getLocals());
			
			for(int i=0; i < (paramCount - off); i++) {
				Expr p = params[i + off];
				makeFact(p, valueResolver);
			}
		}
		
		private ArgumentFact makeFact(Expr e, LocalValueResolver valueResolver) {
			if(e.getOpcode() == CONST_LOAD) {
				return new ArgumentFact.ConstantValueFact(((ConstantExpr) e).getConstant());
			}
			return ArgumentFact.AnyValueFact.INSTANCE;
		}
	}
	
	public static class CallGraph extends FastDirectedGraph<MethodNode, ContextSensitiveInvocation> {

		public static class ContextSensitiveInvocation extends FastGraphEdge<MethodNode> {
			public final Expr call;
			
			public ContextSensitiveInvocation(MethodNode src, MethodNode dst, Expr call) {
				super(src, dst);
				this.call = call;
			}
		}
		
		@Override
		public boolean excavate(MethodNode n) {
			throw new UnsupportedOperationException("TODO");
		}

		@Override
		public boolean jam(MethodNode pred, MethodNode succ, MethodNode n) {
			throw new UnsupportedOperationException("TODO");
		}

		@Override
		public ContextSensitiveInvocation clone(ContextSensitiveInvocation edge, MethodNode oldN, MethodNode newN) {
			throw new UnsupportedOperationException("TODO");
		}

		@Override
		public ContextSensitiveInvocation invert(ContextSensitiveInvocation edge) {
			throw new UnsupportedOperationException("TODO");
		}

		@Override
		public FastGraph<MethodNode, ContextSensitiveInvocation> copy() {
			throw new UnsupportedOperationException("TODO");
		}
	}
}