package org.rsdeob.test;

import org.junit.Test;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.FlowEdge;
import org.rsdeob.stdlib.cfg.FlowEdge.ImmediateEdge;
import org.rsdeob.stdlib.collections.graph.FastBlockGraph;

import static org.junit.Assert.assertTrue;

public class FastBlockGraphTest {

	@Test
	public void testContainsVertex() {
		FastBlockGraph graph = new TestFastBlockGraph();
		BasicBlock a, b, c;
		graph.addVertex(a = new BasicBlock(null, "A", null));
		graph.addVertex(b = new BasicBlock(null, "B", null));
		graph.addVertex(c = new BasicBlock(null, "C", null));
		assertTrue(graph.size() == 3);
		assertTrue(graph.containsVertex(a));
		assertTrue(graph.containsVertex(b));
		assertTrue(graph.containsVertex(c));
	}

	@Test
	public void removeVertex() {
		FastBlockGraph graph = new TestFastBlockGraph();
		BasicBlock a, b, c, d, e, f, g;
		graph.addVertex(a = new BasicBlock(null, "A", null));
		graph.addVertex(b = new BasicBlock(null, "B", null));
		graph.addVertex(c = new BasicBlock(null, "C", null));
		graph.addVertex(d = new BasicBlock(null, "D", null));
		graph.addVertex(e = new BasicBlock(null, "E", null));
		graph.addVertex(f = new BasicBlock(null, "F", null));
		graph.addVertex(g = new BasicBlock(null, "G", null));

//		 immediate just to simply creation
		graph.addEdge(a, new FlowEdge.ImmediateEdge(a, b));
		graph.addEdge(a, new ImmediateEdge(a, c));
		graph.addEdge(a, new ImmediateEdge(a, e));

		graph.addEdge(b, new ImmediateEdge(b, f));

		graph.addEdge(c, new ImmediateEdge(c, d));
		graph.addEdge(c, new ImmediateEdge(c, e));

		graph.addEdge(d, new ImmediateEdge(d, e));

		graph.addEdge(e, new ImmediateEdge(e, g));

		graph.addEdge(f, new ImmediateEdge(f, g));

		System.out.println(graph);

		graph.removeVertex(c);
		System.out.println();
		System.out.println(graph);

		graph.removeVertex(b);
		System.out.println();
		System.out.println(graph);
	}

	private class TestFastBlockGraph extends FastBlockGraph {
		@Override
		public BasicBlock getEntry() {
			return null;
		}
	}
}
