package org.mapleir.ir.cfg.builder;

import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.stdlib.collections.graph.algorithms.SimpleDfs;

import java.util.HashSet;
import java.util.Set;

public class DeadBlocksPass extends ControlFlowGraphBuilder.BuilderPass {

	public DeadBlocksPass(ControlFlowGraphBuilder builder) {
		super(builder);
	}

	@Override
	public void run() {
		assert(builder.graph.getEntries().size() == 1);
		Set<BasicBlock> reachable = new HashSet<>(SimpleDfs.preorder(builder.graph, builder.graph.getEntries().iterator().next()));
		Set<BasicBlock> unreachable = new HashSet<>(builder.graph.vertices());
		unreachable.removeAll(reachable);
		for (BasicBlock b : unreachable) {
			System.out.println("Deleting " + b);
			builder.graph.removeVertex(b);
		}
	}
}
