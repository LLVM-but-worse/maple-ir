package org.mapleir.ir.cfg.builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.locals.Local;
import org.mapleir.stdlib.cfg.edge.FlowEdge;
import org.mapleir.stdlib.cfg.edge.FlowEdges;
import org.mapleir.stdlib.cfg.edge.TryCatchEdge;
import org.mapleir.stdlib.collections.graph.flow.ExceptionRange;

public class NaturalisationPass1 extends ControlFlowGraphBuilder.BuilderPass {

	public NaturalisationPass1(ControlFlowGraphBuilder builder) {
		super(builder);
	}

	@Override
	public void run() {
		mergeImmediates();
	}
	
	int mergeImmediates() {
		class MergePair {
			final BasicBlock src;
			final BasicBlock dst;
			MergePair(BasicBlock src, BasicBlock dst)  {
				this.src = src;
				this.dst = dst;
			}
		}
		
		List<MergePair> merges = new ArrayList<>();
		Map<BasicBlock, BasicBlock> remap = new HashMap<>();
		Map<BasicBlock, List<ExceptionRange<BasicBlock>>> ranges = new HashMap<>();
		
		for(BasicBlock b : builder.graph.vertices()) {
			BasicBlock in = b.getIncomingImmediate();
			if(in == null) {
				continue;
			}
			Set<FlowEdge<BasicBlock>> inSuccs = in.getSuccessors(e -> !(e instanceof TryCatchEdge));
			if(inSuccs.size() != 1 || builder.graph.getReverseEdges(b).size() != 1) {
				continue;
			}
			
			List<ExceptionRange<BasicBlock>> range1 = b.getProtectingRanges();
			List<ExceptionRange<BasicBlock>> range2 = in.getProtectingRanges();
			
			if(!range1.equals(range2)) {
				continue;
			}
			
			ranges.put(b, range1);
			ranges.put(in, range2);
			
			merges.add(new MergePair(in, b));
			
			remap.put(in, in);
			remap.put(b, b);
		}
		
		for(MergePair p : merges) {
			BasicBlock src = remap.get(p.src);
			BasicBlock dst = p.dst;
			
			dst.transfer(src);
			
			for(FlowEdge<BasicBlock> e : builder.graph.getEdges(dst)) {
				// since the ranges are the same, we don't need
				// to clone these.
				if(e.getType() != FlowEdges.TRYCATCH) {
					BasicBlock edst = e.dst;
					edst = remap.getOrDefault(edst, edst);
					builder.graph.addEdge(src, e.clone(src, edst));
				}
			}
			builder.graph.removeVertex(dst);
			
			remap.put(dst, src);
			
			for(ExceptionRange<BasicBlock> r : ranges.get(src)) {
				r.removeVertex(dst);
			}
			for(ExceptionRange<BasicBlock> r : ranges.get(dst)) {
				r.removeVertex(dst);
			}
			
			// System.out.printf("Merged %s into %s.%n", dst.getId(), src.getId());
		}
		
		// we need to update the assigns map if we change the cfg.
		for(Entry<Local, Set<BasicBlock>> e : builder.assigns.entrySet()) {
			Set<BasicBlock> set = e.getValue();
			Set<BasicBlock> copy = new HashSet<>(set);
			for(BasicBlock b : copy) {
				BasicBlock r = remap.getOrDefault(b, b);
				if(r != b) {
					set.remove(b);
					set.add(r);
				}
			}
		}
		
		return merges.size();
	}
}