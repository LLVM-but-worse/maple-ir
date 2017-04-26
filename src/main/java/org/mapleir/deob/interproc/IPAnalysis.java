package org.mapleir.deob.interproc;

import org.mapleir.context.IContext;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.invoke.Invocation;
import org.mapleir.ir.code.stmt.copy.CopyVarStmt;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class IPAnalysis extends IRCallTracer implements Opcode {
	
	public static IPAnalysis create(IContext cxt, IPAnalysisVisitor... visitors) {
		List<IPAnalysisVisitor> cvs = new ArrayList<>();
		for(IPAnalysisVisitor v : visitors) {
			cvs.add(v);
		}
		return create(cxt, cvs);
	}
	
	public static IPAnalysis create(IContext cxt, List<IPAnalysisVisitor> visitors) {
		IPAnalysis analysis = new IPAnalysis(cxt, visitors);
		for(MethodNode mn : cxt.getIRCache().getActiveMethods()) {
			analysis.trace(mn);
		}
		return analysis;
	}
	
	private final List<IPAnalysisVisitor> visitors;
	private final Map<MethodNode, Set<Invocation>> calls;
	private final Map<MethodNode, List<List<Expr>>> parameterInputs;
	private final Map<MethodNode, int[]> paramIndices;
	
	public IPAnalysis(IContext cxt, List<IPAnalysisVisitor> visitors) {
		super(cxt);
		this.visitors = visitors;
		
		calls = new HashMap<>();
		parameterInputs = new HashMap<>();
		paramIndices = new HashMap<>();
	}
	
	public Set<Invocation> getCallsTo(MethodNode m) {
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
		for(IPAnalysisVisitor v : visitors) {
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
		
		ControlFlowGraph cfg = context.getIRCache().getFor(m);
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
		
		for(IPAnalysisVisitor v : visitors) {
			v.postVisitMethod(this, m);
		}
	}
	
	@Override
	protected void processedInvocation(MethodNode caller, MethodNode callee, Invocation e) {
		for(IPAnalysisVisitor v : visitors) {
			v.preProcessedInvocation(this, caller, callee, e);
		}
		
		if(context.getApplication().isLibraryClass(callee.owner.name)) {
			return;
		}
		
		calls.get(callee).add(e);
		
		Expr[] params = e.getParameterExprs();
		
		for(int i=0; i < params.length; i++) {
			parameterInputs.get(callee).get(i).add(params[i]);
		}
		
		for(IPAnalysisVisitor v : visitors) {
			v.postProcessedInvocation(this, caller, callee, e);
		}
	}
	
}