package org.mapleir.deobimpl2.util;

import org.mapleir.IRCallTracer;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.InitialisedObjectExpr;
import org.mapleir.ir.code.expr.InvocationExpr;
import org.mapleir.ir.code.stmt.copy.CopyVarStmt;
import org.mapleir.stdlib.deob.IContext;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IPConstAnalysis extends IRCallTracer implements Opcode {
	
	public static IPConstAnalysis create(IContext cxt, ChildVisitor... visitors) {
		List<ChildVisitor> cvs = new ArrayList<>();
		for(ChildVisitor v : visitors) {
			cvs.add(v);
		}
		return create(cxt, cvs);
	}
	
	public static IPConstAnalysis create(IContext cxt, List<ChildVisitor> visitors) {
		IPConstAnalysis analysis = new IPConstAnalysis(cxt, visitors);
		for(MethodNode mn : cxt.getCFGS().getActiveMethods()) {
			analysis.trace(mn);
		}
		return analysis;
	}
	
	private final List<ChildVisitor> visitors;
	private final Map<MethodNode, Set<Expr>> calls;
	private final Map<MethodNode, List<List<Expr>>> parameterInputs;
	private final Map<MethodNode, int[]> paramIndices;
	
	public IPConstAnalysis(IContext cxt, List<ChildVisitor> visitors) {
		super(cxt);
		this.visitors = visitors;
		
		calls = new HashMap<>();
		parameterInputs = new HashMap<>();
		paramIndices = new HashMap<>();
	}
	
	public Set<Expr> getCallsTo(MethodNode m) {
		return calls.get(m);
	}

	public int getLocalIndex(MethodNode m, int i) {
		int[] idxs = paramIndices.get(m);
		return idxs[i];
	}
	
	public int getParameterCount(MethodNode m) {
		if(paramIndices.containsKey(m) && parameterInputs.containsKey(m)) {
			int i1 = paramIndices.get(m).length;
			int i2 = parameterInputs.get(m).size();
			if(i1 != i2) {
				throw new IllegalStateException(String.format("%s | %d:%d | %s : %s", m, i1, i2, Arrays.toString(paramIndices.get(m)), parameterInputs.get(m)));
			}
			return i1;
		} else {
			throw new UnsupportedOperationException(m.toString());
		}
	}

	public List<List<Expr>> getInputs(MethodNode method) {
		return parameterInputs.get(method);
	}
	
	public List<Expr> getInputs(MethodNode method, int paramIndex) {
		/*if(calls.containsKey(method)) {
			int[] idxs = paramIndices.get(method);
			int lvtIndex = idxs[paramIndex];
			
			List<List<Expr>> mInputs = parameterInputs.get(method);
			return mInputs.get(lvtIndex);
		} else {
			return null;
		}*/
		return parameterInputs.get(method).get(paramIndex);
	}
	
	@Override
	protected void visitMethod(MethodNode m) {
		for(ChildVisitor v : visitors) {
			v.preVisitMethod(this, m);
		}
		
		if(context.getApplication().isLibraryClass(m.owner.name)) {
			return;
		}
		
		boolean isStatic = (m.access & Opcodes.ACC_STATIC) != 0;
		
		int paramCount = Type.getArgumentTypes(m.desc).length;
		int off = (isStatic ? 0 : 1);
		int synthCount = paramCount + off;
		List<List<Expr>> lists = new ArrayList<>(synthCount);
		
		/* Create a mapping between the actual variable table
		 * indices and the parameter indices in the method
		 * descriptor. */
		int[] idxs = new int[synthCount];
		
		ControlFlowGraph cfg = context.getCFGS().getIR(m);
		BasicBlock entry = cfg.getEntries().iterator().next();
		
		/* static:
		 *  first arg = 0
		 *
		 * non-static:
		 *  this = 0
		 *  first arg = 1*/
		int paramIndex = 0;
		for(Stmt stmt : entry) {
			if(stmt.getOpcode() == LOCAL_STORE) {
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
		
		for(ChildVisitor v : visitors) {
			v.postVisitMethod(this, m);
		}
	}
	
	@Override
	protected void processedInvocation(MethodNode caller, MethodNode callee, Expr e) {
		for(ChildVisitor v : visitors) {
			v.preProcessedInvocation(this, caller, callee, e);
		}
		
		if(context.getApplication().isLibraryClass(callee.owner.name)) {
			return;
		}
		
		calls.get(callee).add(e);
		
		Expr[] params;
		
		if(e.getOpcode() == INVOKE) {
			params = ((InvocationExpr) e).getParameterArguments();
		} else if(e.getOpcode() == INIT_OBJ) {
			params = ((InitialisedObjectExpr) e).getArgumentExpressions();
		} else {
			throw new UnsupportedOperationException(String.format("%s -> %s (%s)", caller, callee, e));
		}
		
		for(int i=0; i < params.length; i++) {
			parameterInputs.get(callee).get(i).add(params[i]);
		}
		
		for(ChildVisitor v : visitors) {
			v.postProcessedInvocation(this, caller, callee, e);
		}
	}
	
	public static interface ChildVisitor {
		
		default void preVisitMethod(IPConstAnalysis analysis, MethodNode m) {}
		
		default void postVisitMethod(IPConstAnalysis analysis, MethodNode m) {}
		
		default void preProcessedInvocation(IPConstAnalysis analysis, MethodNode caller, MethodNode callee, Expr e) {}
		
		default void postProcessedInvocation(IPConstAnalysis analysis, MethodNode caller, MethodNode callee, Expr e) {}
	}
}