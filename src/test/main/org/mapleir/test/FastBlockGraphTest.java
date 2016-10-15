package org.mapleir.test;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.mapleir.ir.cfg.BasicBlock;
import org.mapleir.ir.cfg.FastBlockGraph;
import org.mapleir.ir.cfg.edge.ImmediateEdge;

public class FastBlockGraphTest {

	@Test
	public void testContainsVertex() {
		FastBlockGraph graph = new TestFastBlockGraph();
		BasicBlock a, b, c;
		int index = 0;
		graph.addVertex(a = new BasicBlock(null, index++, null));
		graph.addVertex(b = new BasicBlock(null, index++, null));
		graph.addVertex(c = new BasicBlock(null, index++, null));
		assertTrue(graph.size() == 3);
		assertTrue(graph.containsVertex(a));
		assertTrue(graph.containsVertex(b));
		assertTrue(graph.containsVertex(c));
	}

	@Test
	public void removeVertex() {
		FastBlockGraph graph = new TestFastBlockGraph();
		BasicBlock a, b, c, d, e, f, g;
		int index = 0;
		graph.addVertex(a = new BasicBlock(null, index++, null));
		graph.addVertex(b = new BasicBlock(null, index++, null));
		graph.addVertex(c = new BasicBlock(null, index++, null));
		graph.addVertex(d = new BasicBlock(null, index++, null));
		graph.addVertex(e = new BasicBlock(null, index++, null));
		graph.addVertex(f = new BasicBlock(null, index++, null));
		graph.addVertex(g = new BasicBlock(null, index++, null));

//		 immediate just to simply creation
		graph.addEdge(a, new ImmediateEdge<>(a, b));
		graph.addEdge(a, new ImmediateEdge<>(a, c));
		graph.addEdge(a, new ImmediateEdge<>(a, e));

		graph.addEdge(b, new ImmediateEdge<>(b, f));

		graph.addEdge(c, new ImmediateEdge<>(c, d));
		graph.addEdge(c, new ImmediateEdge<>(c, e));

		graph.addEdge(d, new ImmediateEdge<>(d, e));

		graph.addEdge(e, new ImmediateEdge<>(e, g));

		graph.addEdge(f, new ImmediateEdge<>(f, g));

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
		int index = 0;
		graph.addVertex(a = new BasicBlock(null, index++, null));
		graph.addVertex(b = new BasicBlock(null, index++, null));
		graph.addVertex(c = new BasicBlock(null, index++, null));
		graph.addVertex(d = new BasicBlock(null, index++, null));
		graph.addVertex(e = new BasicBlock(null, index++, null));
		graph.addVertex(f = new BasicBlock(null, index++, null));
		graph.addVertex(g = new BasicBlock(null, index++, null));

//		 immediate just to simplify creation
		graph.addEdge(a, new ImmediateEdge<>(a, b));
		graph.addEdge(a, new ImmediateEdge<>(a, c));
		graph.addEdge(a, new ImmediateEdge<>(a, e));

		graph.addEdge(b, new ImmediateEdge<>(b, f));

		graph.addEdge(c, new ImmediateEdge<>(c, d));
		graph.addEdge(c, new ImmediateEdge<>(c, e));

		graph.addEdge(d, new ImmediateEdge<>(d, e));

		graph.addEdge(e, new ImmediateEdge<>(e, g));

		graph.addEdge(f, new ImmediateEdge<>(f, g));

		System.out.println(graph);
		System.out.println();

		BasicBlock h = new BasicBlock(null, index++, null);
		graph.replace(b, h);

		System.out.println(graph);
	}

	@Test
	public void excavateTest() {
		FastBlockGraph graph = new TestFastBlockGraph();
		BasicBlock a, b, c, d, e, f, g;
		int index = 0;
		graph.addVertex(a = new BasicBlock(null, index++, null));
		graph.addVertex(b = new BasicBlock(null, index++, null));
		graph.addVertex(c = new BasicBlock(null, index++, null));
		graph.addVertex(d = new BasicBlock(null, index++, null));
		graph.addVertex(e = new BasicBlock(null, index++, null));
		graph.addVertex(f = new BasicBlock(null, index++, null));
		graph.addVertex(g = new BasicBlock(null, index++, null));

		graph.addEdge(a, new ImmediateEdge<>(a, b));
		graph.addEdge(a, new ImmediateEdge<>(a, c));
		graph.addEdge(a, new ImmediateEdge<>(a, e));

		graph.addEdge(b, new ImmediateEdge<>(b, f));

		graph.addEdge(c, new ImmediateEdge<>(c, d));
		graph.addEdge(c, new ImmediateEdge<>(c, e));

		graph.addEdge(d, new ImmediateEdge<>(d, e));

		graph.addEdge(e, new ImmediateEdge<>(e, g));

		graph.addEdge(f, new ImmediateEdge<>(f, g));

		System.out.println("preexcavate");
		System.out.println(graph);

		graph.excavate(b);
		System.out.println("postexcavate");
		System.out.println(graph);
	}

	private class TestFastBlockGraph extends FastBlockGraph {
	}
}
