package org.mapleir.stdlib.collections.graph.algorithms;

import java.io.IOException;
import java.util.List;

import org.mapleir.stdlib.collections.graph.GraphConverter;
import org.mapleir.stdlib.collections.graph.OrderedNode;
import org.mapleir.stdlib.collections.graph.OrderedNode.ODirectedGraph;
import org.mapleir.stdlib.collections.graph.OrderedNode.OGraph;

import junit.framework.TestCase;


public class DfsTest extends TestCase {

	public void testSimpleDfs() throws IOException {
		ODirectedGraph g = (ODirectedGraph) GraphConverter.fromFile("/dfspre1.gv");
		SimpleDfs<OrderedNode> dfs = new SimpleDfs<OrderedNode>(g, getNode(g, 1), SimpleDfs.PRE | SimpleDfs.POST);
		List<OrderedNode> res = dfs.getPreOrder();
		assertOrdered(res, g.size());
	}
	
	public void testExtendedDfsPre1() throws IOException {
		ODirectedGraph g = (ODirectedGraph) GraphConverter.fromFile("/dfspre1.gv");
		ExtendedDfs<OrderedNode> dfs = new ExtendedDfs<>(g, ExtendedDfs.PRE).run(getNode(g, 1));
		List<OrderedNode> res = dfs.getPreOrder();
		assertOrdered(res, g.size());
	}
	
	public void testExtendedDfsPost1() throws IOException {
		ODirectedGraph g = (ODirectedGraph) GraphConverter.fromFile("/dfspost1.gv");
		ExtendedDfs<OrderedNode> dfs = new ExtendedDfs<>(g, ExtendedDfs.POST).run(getNode(g, 9));
		List<OrderedNode> res = dfs.getPostOrder();
		assertOrdered(res, g.size());
	}
	
	private static void assertOrdered(List<OrderedNode> nodes, int ex) {
		assertEquals("missing nodes", ex, nodes.size());
		for(int i=0; i < nodes.size(); i++) {
			assertEquals(nodes.toString(), i + 1, nodes.get(i).time);
		}
	}
	
	private static OrderedNode getNode(OGraph graph, int time) {
		for(OrderedNode n : graph.vertices()) {
			if(n.time == time) {
				return n;
			}
		}
		throw new IllegalStateException(String.format("graph does not contain node with id %d", time));
	}
}
