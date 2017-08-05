package org.mapleir.deob.interproc.callgraph;

import org.mapleir.context.AnalysisContext;
import org.mapleir.stdlib.collections.graph.algorithms.TarjanSCC;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public class CallGraphReducer {
	private final AnalysisContext cxt;

	public CallGraphReducer(AnalysisContext cxt) {
		this.cxt = cxt;
	}

	public SiteSensitiveCallDAG eliminateSCCs(CallSiteSensitiveCallGraph cg) {
		// compute sccs
		TarjanSCC<CallGraphNode> sccComputor = new TarjanSCC<>(cg);
		for(MethodNode m : cxt.getApplicationContext().getEntryPoints()) {
			CallGraphNode.CallReceiverNode node = cg.getNode(m);

			if(cg.getReverseEdges(node).size() > 0) {
				throw new RuntimeException("entry called?");
			}

			sccComputor.search(node);
		}

		// populate graph
		SiteSensitiveCallDAG result = new SiteSensitiveCallDAG();
		for(List<CallGraphNode> scc : sccComputor.getComponents()) {
			result.addVertex(result.createNode(scc));
			System.out.println(scc);
		}

		// link sccs
		for(SiteSensitiveCallDAG.MultiCallGraphNode srcSccNode : result.vertices()) {
			for (CallGraphNode srcCgNode : srcSccNode) {
				for (CallGraphEdge e : cg.getEdges(srcCgNode)) {
					CallGraphNode dstCgNode = e.dst();
					SiteSensitiveCallDAG.MultiCallGraphNode dstSccNode = result.findSCCOf(dstCgNode);
					if (dstSccNode != srcSccNode)
						result.addEdge(srcSccNode, new SiteSensitiveCallDAG.SiteSensitiveCallDAGEdge(srcSccNode, dstSccNode));
				}
			}
		}
		return result;
	}
}
