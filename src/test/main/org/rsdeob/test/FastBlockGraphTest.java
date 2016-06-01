package org.rsdeob.test;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.FastBlockGraph;
import org.rsdeob.stdlib.cfg.edge.ImmediateEdge;

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
		graph.addEdge(a, new ImmediateEdge<BasicBlock>(a, b));
		graph.addEdge(a, new ImmediateEdge<BasicBlock>(a, c));
		graph.addEdge(a, new ImmediateEdge<BasicBlock>(a, e));

		graph.addEdge(b, new ImmediateEdge<BasicBlock>(b, f));

		graph.addEdge(c, new ImmediateEdge<BasicBlock>(c, d));
		graph.addEdge(c, new ImmediateEdge<BasicBlock>(c, e));

		graph.addEdge(d, new ImmediateEdge<BasicBlock>(d, e));

		graph.addEdge(e, new ImmediateEdge<BasicBlock>(e, g));

		graph.addEdge(f, new ImmediateEdge<BasicBlock>(f, g));

//		System.out.println(graph);

		graph.removeVertex(c);
//		System.out.println();
//		System.out.println(graph);

		graph.removeVertex(b);
//		System.out.println();
//		System.out.println(graph);
	}

	@Test
	public void replaceTest() {
		FastBlockGraph graph = new TestFastBlockGraph();
		BasicBlock a, b, c, d, e, f, g;
		graph.addVertex(a = new BasicBlock(null, "A", null));
		graph.addVertex(b = new BasicBlock(null, "B", null));
		graph.addVertex(c = new BasicBlock(null, "C", null));
		graph.addVertex(d = new BasicBlock(null, "D", null));
		graph.addVertex(e = new BasicBlock(null, "E", null));
		graph.addVertex(f = new BasicBlock(null, "F", null));
		graph.addVertex(g = new BasicBlock(null, "G", null));

//		 immediate just to simplify creation
		graph.addEdge(a, new ImmediateEdge<BasicBlock>(a, b));
		graph.addEdge(a, new ImmediateEdge<BasicBlock>(a, c));
		graph.addEdge(a, new ImmediateEdge<BasicBlock>(a, e));

		graph.addEdge(b, new ImmediateEdge<BasicBlock>(b, f));

		graph.addEdge(c, new ImmediateEdge<BasicBlock>(c, d));
		graph.addEdge(c, new ImmediateEdge<BasicBlock>(c, e));

		graph.addEdge(d, new ImmediateEdge<BasicBlock>(d, e));

		graph.addEdge(e, new ImmediateEdge<BasicBlock>(e, g));

		graph.addEdge(f, new ImmediateEdge<BasicBlock>(f, g));

		System.out.println(graph);
		System.out.println();

		BasicBlock h = new BasicBlock(null, "H", null);
		graph.replace(b, h);

		System.out.println(graph);
	}

	@Test
	public void excavateTest() {
		FastBlockGraph graph = new TestFastBlockGraph();
		BasicBlock a, b, c, d, e, f, g;
		graph.addVertex(a = new BasicBlock(null, "A", null));
		graph.addVertex(b = new BasicBlock(null, "B", null));
		graph.addVertex(c = new BasicBlock(null, "C", null));
		graph.addVertex(d = new BasicBlock(null, "D", null));
		graph.addVertex(e = new BasicBlock(null, "E", null));
		graph.addVertex(f = new BasicBlock(null, "F", null));
		graph.addVertex(g = new BasicBlock(null, "G", null));

		graph.addEdge(a, new ImmediateEdge<BasicBlock>(a, b));
		graph.addEdge(a, new ImmediateEdge<BasicBlock>(a, c));
		graph.addEdge(a, new ImmediateEdge<BasicBlock>(a, e));

		graph.addEdge(b, new ImmediateEdge<BasicBlock>(b, f));

		graph.addEdge(c, new ImmediateEdge<BasicBlock>(c, d));
		graph.addEdge(c, new ImmediateEdge<BasicBlock>(c, e));

		graph.addEdge(d, new ImmediateEdge<BasicBlock>(d, e));

		graph.addEdge(e, new ImmediateEdge<BasicBlock>(e, g));

		graph.addEdge(f, new ImmediateEdge<BasicBlock>(f, g));
		
		System.out.println("preexcavate");
		System.out.println(graph);
	
		graph.excavate(b);
		System.out.println("postexcavate");
		System.out.println(graph);
	}
	
	private class TestFastBlockGraph extends FastBlockGraph {
	}
}
