package org.mapleir.deob.interproc.cxtsenscg;

import org.mapleir.context.AnalysisContext;
import org.mapleir.deob.interproc.geompa.PAG;
import org.mapleir.deob.interproc.geompa.util.QueueReader;
import org.mapleir.res.InvocationResolver4;
import org.objectweb.asm.tree.MethodNode;

public class OFCG {

	private final PAG pag;
	private final OFCGB ofcgb;
	private final ReachabilityMatrix rm;
    private final QueueReader<MethodNode> reachablesReader;
    private final QueueReader<Edge> callEdges;
	private final CallGraph callGraph;
	
	public OFCG(AnalysisContext cxt, InvocationResolver4 resolver, PAG pag, boolean appOnly) {
		this.pag = pag;
		callGraph = new CallGraph();
		rm = new ReachabilityMatrix(callGraph, CGBuilder.getEntryPoints(cxt.getApplication()).iterator());
		
		ContextManager cm = new ContextInsensitiveContextManager(callGraph);
		ofcgb = new OFCGB(cxt, resolver, rm, cm);
		
		reachablesReader = rm.listener();
		callEdges = cm.callGraph().listener();
	}

	public void build() {
		ofcgb.processReachables();
		processReachables();
		processCallEdges();
	}
	
	private void processReachables() {
		rm.update();
		while (reachablesReader.hasNext()) {
			MethodNode m = reachablesReader.next();
			MethodPAG mpag = MethodPAG.get(pag, m);
			mpag.build();
			mpag.addToPAG();
		}
	}

	private void processCallEdges() {
		while (callEdges.hasNext()) {
			Edge e = callEdges.next();
			MethodPAG amp = MethodPAG.v(pag, e.tgt());
			amp.build();
			amp.addToPAG();
			pag.addCallTarget(e);
		}
	}
}