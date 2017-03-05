package org.mapleir.test;

// import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.mapleir.stdlib.collections.graph.algorithms.SimpleDfs;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.FastBlockGraph;
import org.mapleir.ir.cfg.edge.ImmediateEdge;
import org.mapleir.stdlib.collections.graph.algorithms.TarjanDominanceComputor;

public class DominanceTest {

	@Test
	public void test() {
		FastBlockGraph graph = new FastBlockGraph();
		BasicBlock b0 = block();
		BasicBlock b1 = block();
		BasicBlock b2 = block();
		BasicBlock b3 = block();
		BasicBlock b4 = block();
		BasicBlock b5 = block();
		BasicBlock b6 = block();
		BasicBlock b7 = block();
		BasicBlock end = block();
		graph.addVertex(end);

		edge(graph, b0, b1);
		edge(graph, b1, b2);
		edge(graph, b1, b3);
		edge(graph, b2, b7);
		edge(graph, b3, b4);
		edge(graph, b3, b5);
		edge(graph, b4, b6);
		edge(graph, b5, b6);
		edge(graph, b6, b7);
		edge(graph, b7, b1);
		edge(graph, b7, end);

		graph.getEntries().add(b0);

		System.out.println(graph);

		TarjanDominanceComputor<BasicBlock> comp = new TarjanDominanceComputor<>(graph, new SimpleDfs<>(graph, graph.getEntries().iterator().next(), SimpleDfs.PRE).getPreOrder());

		for(BasicBlock b : graph.vertices()) {
			System.out.println(b.getId());
			System.out.println("  frontier=" + comp.frontier(b));
		}

		// results should be
		// node: 0, 1, 2, 3, 4, 5, 6, 7, end
		// df  : -, 1, 7, 7, 6, 6, 7, 1,  -
	}

	static void edge(FastBlockGraph graph, BasicBlock n, BasicBlock s) {
		graph.addEdge(n, new ImmediateEdge<>(n, s));
	}

	private static int index = 0;
	static BasicBlock block() {
		return new BasicBlock(null, index++, null);
	}
}