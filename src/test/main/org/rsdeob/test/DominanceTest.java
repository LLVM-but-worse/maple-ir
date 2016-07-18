package org.rsdeob.test;

// import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.FastBlockGraph;
import org.rsdeob.stdlib.cfg.edge.ImmediateEdge;
import org.rsdeob.stdlib.collections.graph.flow.TarjanDominanceComputor;

public class DominanceTest {

	@Test
	public void test() {
		FastBlockGraph graph = new FastBlockGraph();
		BasicBlock b0 = block("0");
		BasicBlock b1 = block("1");
		BasicBlock b2 = block("2");
		BasicBlock b3 = block("3");
		BasicBlock b4 = block("4");
		BasicBlock b5 = block("5");
		BasicBlock b6 = block("6");
		BasicBlock b7 = block("7");
		BasicBlock end = block("end");
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

		TarjanDominanceComputor<BasicBlock> comp = new TarjanDominanceComputor<>(graph);

		for(BasicBlock b : graph.vertices()) {
			System.out.println(b.getId());
			System.out.println("  frontier=" + comp.frontier(b));
		}

		// results should be
		// node: 0, 1, 2, 3, 4, 5, 6, 7, end
		// df  : -, 1, 7, 7, 6, 6, 7, 1,  -
	}

	static void edge(FastBlockGraph graph, BasicBlock n, BasicBlock s) {
		graph.addEdge(n, new ImmediateEdge<BasicBlock>(n, s));
	}

	static BasicBlock block(String name) {
		return new BasicBlock(null, name, null);
	}
}