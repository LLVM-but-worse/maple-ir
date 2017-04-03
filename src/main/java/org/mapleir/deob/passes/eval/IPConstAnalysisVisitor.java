package org.mapleir.deob.passes.eval;

import org.mapleir.context.IContext;
import org.mapleir.deob.interproc.IPtAnalysisVisitor;
import org.mapleir.deob.interproc.IPAnalysis;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.ConstantExpr;
import org.mapleir.ir.code.expr.InitialisedObjectExpr;
import org.mapleir.ir.code.expr.InvocationExpr;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class IPConstAnalysisVisitor implements IPtAnalysisVisitor {

	final IContext cxt;
	final Map<MethodNode, List<Set<ConstantExpr>>> constParams = new HashMap<>();
	final Map<MethodNode, boolean[]> unconst = new HashMap<>();
	
	public IPConstAnalysisVisitor(IContext cxt) {
		this.cxt = cxt;
	}
	
	@Override
	public void postVisitMethod(IPAnalysis analysis, MethodNode m) {
		int pCount = Type.getArgumentTypes(m.desc).length;
		boolean[] arr = new boolean[pCount];
		
		if(Modifier.isStatic(m.access)) {
			if(!constParams.containsKey(m)) {
				List<Set<ConstantExpr>> l = new ArrayList<>();
				constParams.put(m, l);
				
				for(int i=0; i < pCount; i++) {
					l.add(new HashSet<>());
				}
				
				unconst.put(m, arr);
			}
		} else {
			for(MethodNode site : cxt.getInvocationResolver().resolveVirtualCalls(m, true)) {
				if(!constParams.containsKey(site)) {
					List<Set<ConstantExpr>> l = new ArrayList<>();
					constParams.put(site, l);
					
					for(int i=0; i < pCount; i++) {
						l.add(new HashSet<>());
					}
					
					unconst.put(site, arr);
				}
			}
		}
	}
	
	@Override
	public void postProcessedInvocation(IPAnalysis analysis, MethodNode caller, MethodNode callee, Expr call) {
		Expr[] params;
		
		if(call.getOpcode() == Opcode.INVOKE) {
			params = ((InvocationExpr) call).getParameterArguments();
		} else if(call.getOpcode() == Opcode.INIT_OBJ) {
			params = ((InitialisedObjectExpr) call).getArgumentExpressions();
		} else {
			throw new UnsupportedOperationException(String.format("%s -> %s (%s)", caller, callee, call));
		}
		
		for(int i=0; i < params.length; i++) {
			Expr e = params[i];
			
			if(e.getOpcode() == Opcode.CONST_LOAD) {
				if(Modifier.isStatic(callee.access)) {
					constParams.get(callee).get(i).add((ConstantExpr) e);
				} else {
					/* only chain callsites *can* have this input */
					for(MethodNode site : cxt.getInvocationResolver().resolveVirtualCalls(callee, true)) {
						constParams.get(site).get(i).add((ConstantExpr) e);
					}
				}
			} else {
				/* callsites tainted */
				if(Modifier.isStatic(callee.access)) {
					unconst.get(callee)[i] = true;
				} else {
					/* only chain callsites *can* have this input */
					for(MethodNode site : cxt.getInvocationResolver().resolveVirtualCalls(callee, true)) {
						unconst.get(site)[i] = true;
					}
				}
			}
		}
	}
}
