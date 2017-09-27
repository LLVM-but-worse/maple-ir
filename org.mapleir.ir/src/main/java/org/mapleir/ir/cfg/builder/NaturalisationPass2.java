package org.mapleir.ir.cfg.builder;

import java.util.ArrayList;
import java.util.List;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.stdlib.collections.graph.algorithms.TarjanSCC;

public class NaturalisationPass2 extends ControlFlowGraphBuilder.BuilderPass {

	public NaturalisationPass2(ControlFlowGraphBuilder builder) {
		super(builder);
	}

	@Override
	public void run() {
		TarjanSCC<BasicBlock> scc = new TarjanSCC<>(builder.graph);
		for(BasicBlock b : builder.graph.vertices()) {
			if(scc.low(b) == -1) {
				scc.search(b);
			}
		}
		
		List<BasicBlock> order = new ArrayList<>();
		for(List<BasicBlock> c : scc.getComponents()) {
			order.addAll(c);
		}
		
		builder.graph.naturalise(order);
	}
}