package org.mapleir.deob.interproc.sensitive;

import java.util.List;

import org.mapleir.context.IContext;
import org.mapleir.deob.interproc.IRCallTracer;
import org.mapleir.deob.interproc.sensitive.ContextSensitiveIPAnalysis.CallGraph.ContextInsensitiveInvocation;
import org.mapleir.deob.intraproc.eval.ExpressionEvaluator;
import org.mapleir.deob.intraproc.eval.LocalValueResolver;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.expr.invoke.Invocation;
import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.algorithms.TarjanSCC;
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
		
		TarjanSCC<MethodNode> scc = new TarjanSCC<>(builder.graph);
		for(MethodNode b : builder.graph.vertices()) {
			if(scc.low(b) == -1 && cxt.getIRCache().getActiveMethods().contains(b)) {
				scc.search(b);
			}
		}
		for(List<MethodNode> l : scc.getComponents()) {
			System.out.println(l);
		}
	}
	
	private class CallgraphBuilder extends IRCallTracer implements Opcode {
		
		private final CallGraph graph;
		
		public CallgraphBuilder(IContext context) {
			super(context);
			
			graph = new CallGraph();
		}
		
		@Override
		public void processedInvocation(MethodNode caller, MethodNode callee, Invocation call) {
			Expr[] params = call.getParameterExprs();
			
			boolean isStatic = (callee.access & Opcodes.ACC_STATIC) != 0;
			
			int paramCount = Type.getArgumentTypes(callee.desc).length;
			int off = (isStatic ? 0 : 1);
			
			ControlFlowGraph cfg = context.getIRCache().getFor(caller);
			LocalValueResolver valueResolver = new LocalValueResolver.PoolLocalValueResolver(cfg.getLocals());
			
			for(int i=0; i < (paramCount - off); i++) {
				Expr p = params[i + off];
				makeFact(p, valueResolver);
			}
			
			if(!context.getApplication().isLibraryClass(caller.owner.name)) {
				boolean graphed = false;
				
				if(graph.containsVertex(caller)) {
					for(ContextInsensitiveInvocation i : graph.getEdges(caller)) {
						if(i.dst == callee) {
							graphed = true;
							break;
						}
					}
				}
				
				if(!graphed) {
					graph.addEdge(caller, new ContextInsensitiveInvocation(caller, callee));
				}
			}
		}
		
		private ArgumentFact makeFact(Expr e, LocalValueResolver valueResolver) {
			if(e.getOpcode() == CONST_LOAD) {
				return new ArgumentFact.ConstantValueFact(((ConstantExpr) e).getConstant());
			}
			return ArgumentFact.AnyValueFact.INSTANCE;
		}
	}
	
	public static class CallGraph extends FastDirectedGraph<MethodNode, ContextInsensitiveInvocation> {

		public static class ContextInsensitiveInvocation extends FastGraphEdge<MethodNode> {
			
			public ContextInsensitiveInvocation(MethodNode src, MethodNode dst) {
				super(src, dst);
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
		public ContextInsensitiveInvocation clone(ContextInsensitiveInvocation edge, MethodNode oldN, MethodNode newN) {
			throw new UnsupportedOperationException("TODO");
		}

		@Override
		public ContextInsensitiveInvocation invert(ContextInsensitiveInvocation edge) {
			throw new UnsupportedOperationException("TODO");
		}

		@Override
		public FastGraph<MethodNode, ContextInsensitiveInvocation> copy() {
			throw new UnsupportedOperationException("TODO");
		}
	}
}