package org.mapleir.deob.interproc.sensitive;

import java.util.Set;

import org.mapleir.context.IContext;
import org.mapleir.deob.interproc.IRCallTracer;
import org.mapleir.deob.intraproc.eval.ExpressionEvaluator;
import org.mapleir.deob.intraproc.eval.LocalValueResolver;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.expr.InitialisedObjectExpr;
import org.mapleir.ir.code.expr.InvocationExpr;
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

		public CallgraphBuilder(IContext context) {
			super(context);
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
			Set<ConstantExpr> set = evaluator.evalPossibleValues(valueResolver, e);
			if(e.getOpcode() != Opcode.CONST_LOAD && set != null && set.size() > 0) {
				System.out.println(e + " -> " + set);
			}
			return null;
//			if(e.getOpcode() == CONST_LOAD) {
//				return new ArgumentFact.ConstantValueFact(((ConstantExpr) e).getConstant());
//			} else if(e.getOpcode() == PHI) {
//				System.out.println(e + " -> " + evaluator.evalPossibleValues(valueResolver, e));
//			}
//			return ArgumentFact.AnyValueFact.INSTANCE;
		}
	}
}