package org.mapleir.stdlib.collections.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.mapleir.stdlib.collections.graph.FakeFastDirectedGraph.FakeFastEdge;
import org.mapleir.stdlib.collections.graph.FakeFastDirectedGraph.FakeFastVertex;

import com.google.common.collect.Lists;

import junit.framework.TestCase;

public class FastDirectedGraphTest extends TestCase {
	
	public void testAddVertex() {
		FakeFastDirectedGraph g = graph();
		assertEquals(0, g.size());
//		assertEquals(0, g.reverseSize());
		g.addVertex(node(1));
		assertEquals(1, g.size());
//		assertEquals(1, g.reverseSize());
		// add same node
		g.addVertex(node(1));
		assertEquals(1, g.size());
//		assertEquals(1, g.reverseSize());
	}
	
	public void testAddVertexByAddEdge() {
		FakeFastDirectedGraph g = graph();
		FakeFastEdge e = edge(1, 2);
		g.addEdge(e);
		
		assertEquals(2, g.size());
//		assertEquals(2, g.reverseSize());
	}
	
	public void testAddEdge() {
		FakeFastDirectedGraph g = graph();
		FakeFastEdge e = edge(1, 2);
		g.addEdge(e);
		
		assertEquals(1, g.getEdges(node(1)).size());
		assertEquals(0, g.getEdges(node(2)).size());
		assertEquals(0, g.getReverseEdges(node(1)).size());
		assertEquals(1, g.getReverseEdges(node(2)).size());
		assertTrue(g.containsEdge(e));
		assertTrue(g.containsReverseEdge(e));
	}
	
	public void testRemoveEdge() {
		FakeFastDirectedGraph g = graph();
		FakeFastEdge e = edge(1, 2);
		g.addEdge(e);
		g.removeEdge(e);
		
		// should still have the nodes (?)
		assertEquals(2, g.size());
		assertFalse(g.containsEdge(e));
		assertFalse(g.containsReverseEdge(e));
	}
	
	public void testRemoveVertex() {
		FakeFastDirectedGraph g = graph();
		FakeFastEdge e1 = edge(1, 2), e2 = edge(1, 3),
				e3 = edge(2, 4), e4 = edge(3, 4);
		g.addEdge(e1);
		g.addEdge(e2);
		g.addEdge(e3);
		g.addEdge(e4);
		
		assertEquals(4, g.size());
		assertTrue(g.containsEdge(e1));
		assertTrue(g.containsEdge(e2));
		assertTrue(g.containsEdge(e3));
		assertTrue(g.containsEdge(e4));

		g.removeVertex(node(2));
		assertEquals(3, g.size());
		assertFalse(g.containsVertex(node(2)));
		assertFalse(g.containsEdge(e1));
		assertTrue(g.containsEdge(e2));
		assertFalse(g.containsEdge(e3));
		assertTrue(g.containsEdge(e4));
	}
	
	public void testReplace() {
		FakeFastDirectedGraph g = graph();
		FakeFastEdge e1 = edge(1, 2), e2 = edge(1, 3), e3 = edge(2, 4), e4 = edge(3, 4);
		g.addEdge(e1);
		g.addEdge(e2);
		g.addEdge(e3);
		g.addEdge(e4);
		
		assertEquals(4, g.size());
		assertTrue(g.containsEdge(e1));
		assertTrue(g.containsEdge(e2));
		assertTrue(g.containsEdge(e3));
		assertTrue(g.containsEdge(e4));
		
		g.replace(node(2), node(5));
		
		assertContainsEdges(getEdges(g), asList(edge(1, 5), edge(1, 3), edge(5, 4), edge(3, 4)));
	}

	private final Map<Integer, FakeFastVertex> nodes = new HashMap<>();
	
	private FakeFastDirectedGraph graph() {
		return new FakeFastDirectedGraph(this);
	}
	
	public FakeFastVertex node(int id) {
		return nodes.computeIfAbsent(id, id2 -> new FakeFastVertex(id2));
	}
	
	public FakeFastEdge edge(int src, int dst) {
		return new FakeFastEdge(node(src), node(dst));
	}
	
	private <E> Collection<E> asList(E... es) { 
		Collection<E> col = new ArrayList<>();
		for(E e : es) {
			col.add(e);
		}
		return col;
	}
	
	private <N extends FastGraphVertex, E extends FastGraphEdge<N>> Set<E> getEdges(FastGraph<N, E> g) {
		Set<E> res = new HashSet<>();
		for (N o : g.vertices()) {
			res.addAll(g.getEdges(o));
		}
		return res;
	}
	
	private <E extends FastGraphEdge<?>> void assertContainsEdges(Collection<E> actual, Collection<E> expecting) {
		assertEquals(expecting.size(), actual.size());

		Iterator<E> expectIt = expecting.iterator();
		outer: while (expectIt.hasNext()) {
			E ex = expectIt.next();

			for (E ac : actual) {
				if (ac.src() == ex.src() && ac.dst() == ex.dst()) {
					continue outer;
				}
			}

			fail(String.format("%s doesn't contain %s", actual, ex));
		}
	}
}
