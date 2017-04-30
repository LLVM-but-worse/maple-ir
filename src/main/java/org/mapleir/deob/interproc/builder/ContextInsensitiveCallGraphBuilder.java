package org.mapleir.deob.interproc.builder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mapleir.context.AnalysisContext;
import org.mapleir.deob.interproc.IRCallTracer;
import org.mapleir.ir.code.Opcode;
import org.mapleir.ir.code.expr.invoke.Invocation;
import org.mapleir.stdlib.collections.graph.algorithms.TarjanSCC;
import org.objectweb.asm.tree.MethodNode;

public class ContextInsensitiveCallGraphBuilder extends IRCallTracer implements Opcode {
	
	private final ContextInsensitiveCallGraph<SingleSiteGraphNode> singleSiteGraph;
	private final Map<MethodNode, SingleSiteGraphNode> singleSiteMap;
	private final Map<SingleSiteGraphNode, MultiSiteGraphNode> multiSiteMap;
	
	public ContextInsensitiveCallGraphBuilder(AnalysisContext context) {
		super(context);
		
		singleSiteGraph = new ContextInsensitiveCallGraph<>();
		singleSiteMap = new HashMap<>();
		multiSiteMap = new HashMap<>();
	}
	
	public MultiSiteGraphNode findMultiNode(MethodNode m) {
		if(singleSiteMap.containsKey(m)) {
			SingleSiteGraphNode n = singleSiteMap.get(m);
			return multiSiteMap.get(n);
		}
		return null;
	}
	
	public SingleSiteGraphNode getSingleNode(MethodNode m) {
		if(singleSiteMap.containsKey(m)) {
			return singleSiteMap.get(m);
		} else {
			SingleSiteGraphNode n = new SingleSiteGraphNode(m);
			singleSiteMap.put(m, n);
			return n;
		}
	}
	
	public ContextInsensitiveCallGraph<MultiSiteGraphNode> createDAG() {
		TarjanSCC<SingleSiteGraphNode> sccComputor = new TarjanSCC<>(singleSiteGraph);

		for(SingleSiteGraphNode m : singleSiteGraph.vertices()) {
			if(sccComputor.low(m) == -1) {
				sccComputor.search(m);
			}
		}

		/* map for edges to homologise (?) node objects, i.e. same node
		 * for same component. */
		ContextInsensitiveCallGraph<MultiSiteGraphNode> newGraph = new ContextInsensitiveCallGraph<>();
		
		for(List<SingleSiteGraphNode> component : sccComputor.getComponents()) {
			Set<MethodNode> set = new HashSet<>();
			MultiSiteGraphNode node = new MultiSiteGraphNode(set, multiSiteMap.size());
			
			for(SingleSiteGraphNode n : component) {
				set.add(n.getMethod());
				multiSiteMap.put(n, node);
			}
			
			newGraph.addVertex(node);
		}
		
		/* sanity */
		if(singleSiteGraph.size() != multiSiteMap.size()) {
			throw new IllegalStateException(String.format("nodes:%d, total component size:%d", singleSiteGraph.size(), multiSiteMap.size()));
		}
		
		int z = 0;
		int w = 0;
		for(List<SingleSiteGraphNode> component : sccComputor.getComponents()) {
			
			if(component.size() > 1) {
				z += component.size();
				w++;
			}
			
			for(SingleSiteGraphNode n : component) {
				MultiSiteGraphNode from = multiSiteMap.get(n);
				
				for(ContextInsensitiveInvocation<SingleSiteGraphNode> edge : singleSiteGraph.getEdges(n)) {
					MultiSiteGraphNode to = multiSiteMap.get(edge.dst);
					
					if(from == to) {
						continue;
					}

					boolean graphed = false;
					
					for(ContextInsensitiveInvocation<MultiSiteGraphNode> e : newGraph.getEdges(from)) {
						if(e.dst == to) {
							graphed = true;
							break;
						}
					}
					
					if(!graphed) {
						ContextInsensitiveInvocation<MultiSiteGraphNode> newEdge = new ContextInsensitiveInvocation<>(from, to);
						newGraph.addEdge(from, newEdge);
					}
				}
			}
		}

		/* sanity */
		if((newGraph.size() + z) - w != singleSiteGraph.size()) {
			throw new IllegalStateException(String.format("oldsize: %d, newsize: %s, wsources: %d, totalcollapsed: %d", singleSiteGraph.size(), newGraph.size(), w, z));
		}
		
		return newGraph;
	}
	
	@Override
	public void processedInvocation(MethodNode caller, MethodNode callee, Invocation call) {
		if(!context.getApplication().isLibraryClass(callee.owner.name)) {
			boolean graphed = false;
			
			if(singleSiteMap.containsKey(callee)) {
				SingleSiteGraphNode node = getSingleNode(callee);
				for(ContextInsensitiveInvocation<SingleSiteGraphNode> i : singleSiteGraph.getReverseEdges(node)) {
					if(i.src.getMethod() == caller) {
						graphed = true;
						break;
					}
				}
			}
			
			if(!graphed) {
				singleSiteGraph.addEdge(getSingleNode(caller), new ContextInsensitiveInvocation<>(getSingleNode(caller), getSingleNode(callee)));
			}
		}
	}
}