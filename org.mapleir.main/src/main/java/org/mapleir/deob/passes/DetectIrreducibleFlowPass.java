package org.mapleir.deob.passes;

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.context.AnalysisContext;
import org.mapleir.deob.IPass;
import org.mapleir.deob.PassContext;
import org.mapleir.deob.PassResult;
import org.mapleir.flowgraph.edges.FlowEdge;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.stdlib.collections.graph.algorithms.ExtendedDfs;
import org.mapleir.stdlib.collections.graph.algorithms.LT79Dom;
import org.objectweb.asm.tree.MethodNode;

public class DetectIrreducibleFlowPass implements IPass {

	@Override
	public String getId() {
		return "Detect-Irreducible-Flow";
	}
	
	@Override
	public PassResult accept(PassContext pcxt) {
		/* (a,b) is a retreating edge if it's what we previously considered a backedge.
		 * aho et al '86: (a,b) is a backedge iff b doms a (and not our previous definition)
		 * if backedges != retreating edges -> irreducible loops */
		AnalysisContext cxt = pcxt.getAnalysis();
		for(Entry<MethodNode, ControlFlowGraph> e : cxt.getIRCache().entrySet()) {
			MethodNode mn = e.getKey();
			ControlFlowGraph cfg = e.getValue();
			
			Set<FlowEdge<BasicBlock>> backEdges = new HashSet<>();
			
			LT79Dom<BasicBlock, FlowEdge<BasicBlock>> dom = new LT79Dom<BasicBlock, FlowEdge<BasicBlock>>(cfg, cfg.getEntries().iterator().next());
			for(BasicBlock b : cfg.vertices()) {
				for(FlowEdge<BasicBlock> edge : cfg.getEdges(b)) {
					if(dom.getDominates(edge.dst()).contains(b)) {
						// dst dominates src
						backEdges.add(edge);
					}
				}
			}
			ExtendedDfs<BasicBlock> dfs = new ExtendedDfs<>(cfg, ExtendedDfs.EDGES).run(cfg.getEntries().iterator().next());
			@SuppressWarnings({ "unchecked", "rawtypes" })
			Set<FlowEdge<BasicBlock>> retreatingEdges = (Set) dfs.getEdges(ExtendedDfs.BACK);
			
			retreatingEdges.removeAll(backEdges);
			
			if(!retreatingEdges.isEmpty()) {
				return PassResult.with(pcxt, this).fatal(new IllegalStateException(String.format("%s contains irreducible loop", mn))).make();
			}
		}
		
		return PassResult.with(pcxt, this).finished().make();
	}
}
