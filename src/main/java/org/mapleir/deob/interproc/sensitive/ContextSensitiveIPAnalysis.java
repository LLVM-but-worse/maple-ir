package org.mapleir.deob.interproc.sensitive;

import java.util.LinkedList;

import org.mapleir.context.AnalysisContext;
import org.mapleir.deob.interproc.builder.ContextInsensitiveCallGraph;
import org.mapleir.deob.interproc.builder.ContextInsensitiveCallGraphBuilder;
import org.mapleir.deob.interproc.builder.MultiSiteGraphNode;
import org.mapleir.deob.intraproc.eval.ExpressionEvaluator;
import org.objectweb.asm.tree.MethodNode;

public class ContextSensitiveIPAnalysis {
	
	private final ExpressionEvaluator evaluator;
	
	public ContextSensitiveIPAnalysis(AnalysisContext cxt, ExpressionEvaluator evaluator) {
		this.evaluator = evaluator;
		
		ContextInsensitiveCallGraphBuilder builder = new ContextInsensitiveCallGraphBuilder(cxt);
		for(MethodNode m : cxt.getApplicationContext().getEntryPoints()) {
			builder.trace(m);
		}
		
		ContextInsensitiveCallGraph<MultiSiteGraphNode> g = builder.createDAG();
		
		LinkedList<CallFrame> worklist = new LinkedList<>();
		for(MethodNode m : cxt.getApplicationContext().getEntryPoints()) {
			
		}
	}
}