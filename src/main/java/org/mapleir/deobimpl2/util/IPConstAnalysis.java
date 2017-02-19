package org.mapleir.deobimpl2.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mapleir.IRCallTracer;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.InitialisedObjectExpr;
import org.mapleir.ir.code.expr.InvocationExpr;
import org.mapleir.ir.code.stmt.copy.CopyVarStmt;
import org.mapleir.stdlib.IContext;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

public class IPConstAnalysis {

	private final Map<MethodNode, Set<Expr>> calls;
	private final Map<MethodNode, int[]> paramIndices;
	
	public IPConstAnalysis() {
		calls = new HashMap<>();
	}
	
	public static IPConstAnalysis create(IContext cxt) {
		IPConstAnalysis analysis = new IPConstAnalysis();
		
		ParameterTracer tracer = analysis.new ParameterTracer(cxt);
		for(MethodNode mn : cxt.getActiveMethods()) {
			tracer.trace(mn);
		}
		
		return analysis;
	}
	
	private class ParameterTracer extends IRCallTracer {

		private final Map<MethodNode, List<List<Expr>>> parameterInputs;
		
		public ParameterTracer(IContext context) {
			super(context);
			parameterInputs = new HashMap<>();
		}

		@Override
		protected void visitMethod(MethodNode m) {
			if(tree.isJDKClass(m.owner)) {
				return;
			}
			
			boolean isStatic = (m.access & Opcodes.ACC_STATIC) != 0;
			
			int paramCount = Type.getArgumentTypes(m.desc).length;
			int synthCount = paramCount + (isStatic ? 0 : 1);
			List<List<Expr>> lists = new ArrayList<>(synthCount);
			
			/* Create a mapping between the actual variable table
			 * indices and the parameter indices in the method
			 * descriptor. */
			int[] idxs = new int[synthCount];
			
			ControlFlowGraph cfg = context.getIR(m);
			BasicBlock entry = cfg.getEntries().iterator().next();
			
			/* static:
			 *  first arg = 0
			 *
			 * non-static:
			 *  this = 0
			 *  first arg = 1*/
			
			int paramIndex = 0;
			for(Stmt stmt : entry) {
				if(stmt.getOpcode() == Opcode.LOCAL_STORE) {
					CopyVarStmt cvs = (CopyVarStmt) stmt;
					if(cvs.isSynthetic()) {
						int varIndex = cvs.getVariable().getLocal().getIndex();
						if (!isStatic && varIndex == 0)
							continue;
						
						idxs[paramIndex++] = varIndex;
						continue;
					}
				}
				break;
			}
			
			for(int j=0; j < paramCount; j++) {
				lists.add(new ArrayList<>());
			}
			
			paramIndices.put(m, idxs);
			parameterInputs.put(m, lists);
			calls.put(m, new HashSet<>());
		}
		
		@Override
		protected void processedInvocation(MethodNode caller, MethodNode callee, Expr e) {
			if(tree.isJDKClass(callee.owner)) {
				return;
			}
			
			calls.get(callee).add(e);
			
			Expr[] params;
			
			if(e.getOpcode() == Opcode.INVOKE) {
				params = ((InvocationExpr) e).getParameterArguments();
			} else if(e.getOpcode() == Opcode.INIT_OBJ) {
				params = ((InitialisedObjectExpr) e).getArgumentExpressions();
			} else {
				throw new UnsupportedOperationException(String.format("%s -> %s (%s)", caller, callee, e));
			}
			
			for(int i=0; i < params.length; i++) {
				parameterInputs.get(callee).get(i).add(params[i]);
			}
		}
	}
}