package org.mapleir.stdlib.collections.graph.algorithms;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.mapleir.stdlib.collections.graph.util.GraphConverter;
import org.mapleir.stdlib.collections.graph.util.OrderedNode;
import org.mapleir.stdlib.collections.graph.util.OrderedNode.ODirectedGraph;
import org.mapleir.stdlib.collections.graph.util.OrderedNode.OGraph;

import junit.framework.TestCase;


public class DfsTest extends TestCase {

	ODirectedGraph g;

	@Override
	public void setUp() {
		try {
			g = (ODirectedGraph) GraphConverter.fromFile("/dfs.gv");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void testSimpleDfsPre() {
		DepthFirstSearch<OrderedNode> dfs = new SimpleDfs<>(g, getNode(g, 1), SimpleDfs.PRE);
		List<OrderedNode> res = dfs.getPreOrder();
		assertPreOrdered(res);
	}

	public void testExtendedDfsPre() {
		DepthFirstSearch<OrderedNode> dfs = new ExtendedDfs<>(g, ExtendedDfs.PRE).run(getNode(g, 1));
		List<OrderedNode> res = dfs.getPreOrder();
		assertPreOrdered(res);
	}

	public void testSimpleDfsTopo() {
		DepthFirstSearch<OrderedNode> dfs = new SimpleDfs<>(g, getNode(g, 1), SimpleDfs.TOPO);
		List<OrderedNode> res = dfs.getTopoOrder();
		assertTopoOrdered(res);
	}

	public void testExtendedDfsTopo() {
		DepthFirstSearch<OrderedNode> dfs = new ExtendedDfs<>(g, ExtendedDfs.TOPO).run(getNode(g, 1));
		List<OrderedNode> res = dfs.getTopoOrder();
		assertTopoOrdered(res);
	}

	private void assertPreOrdered(List<OrderedNode> nodes) {
		Set<OrderedNode> visited = new HashSet<>();
		assertEquals("missing nodes", new HashSet<>(nodes), g.vertices());
		for (int i = 1; i < nodes.size(); i++) {
			OrderedNode node = nodes.get(i);
			visited.add(node);
			OrderedNode prev = nodes.get(i - 1);
			if (g.getSuccessors(prev).anyMatch(x -> !visited.contains(x)))
				assertTrue("unvisited pred", g.getPredecessors(node).anyMatch(prev::equals));
		}
	}

	private void assertTopoOrdered(List<OrderedNode> nodes) {
		Set<OrderedNode> visited = new HashSet<>();
		assertEquals("missing nodes", new HashSet<>(nodes), g.vertices());
		for (OrderedNode node : nodes) {
			visited.add(node);
			assertTrue("unvisited pred", g.getPredecessors(node).allMatch(visited::contains));
		}
	}
	
	public static OrderedNode getNode(OGraph graph, int time) {
		for(OrderedNode n : graph.vertices()) {
			if(n.time == time) {
				return n;
			}
		}
		throw new IllegalStateException(String.format("graph does not contain node with id %d", time));
	}
}
