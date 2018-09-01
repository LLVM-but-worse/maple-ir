package org.mapleir.stdlib.collections.graph.algorithms;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterators;
import org.mapleir.stdlib.collections.graph.GraphConverter;
import org.mapleir.stdlib.collections.graph.OrderedNode;
import org.mapleir.stdlib.collections.graph.OrderedNode.ODirectedGraph;
import org.mapleir.stdlib.collections.graph.OrderedNode.OGraph;

import junit.framework.TestCase;


public class DfsTest extends TestCase {

	ODirectedGraph g;

	@Override
	public void setUp() {
		try {
			g = (ODirectedGraph) GraphConverter.fromFile("/dfspre1.gv");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void testSimpleDfsPre() {
		DepthFirstSearch<OrderedNode> dfs = new SimpleDfs<OrderedNode>(g, getNode(g, 1), SimpleDfs.PRE);
		List<OrderedNode> res = dfs.getPreOrder();
		assertTopoOrdered(res, g.size());
	}

	public void testExtendedDfsPre() {
		DepthFirstSearch<OrderedNode> dfs = new ExtendedDfs<>(g, ExtendedDfs.PRE).run(getNode(g, 1));
		List<OrderedNode> res = dfs.getPreOrder();
		assertTopoOrdered(res, g.size());
	}

	public void testSimpleDfsTopo() {
		DepthFirstSearch<OrderedNode> dfs = new SimpleDfs<OrderedNode>(g, getNode(g, 1), SimpleDfs.TOPO);
		List<OrderedNode> res = dfs.getTopoOrder();
		assertTopoOrdered(res, g.size());
	}

	public void testExtendedDfsTopo() {
		DepthFirstSearch<OrderedNode> dfs = new ExtendedDfs<>(g, ExtendedDfs.TOPO).run(getNode(g, 1));
		List<OrderedNode> res = dfs.getTopoOrder();
		assertTopoOrdered(res, g.size());
	}

	private void assertTopoOrdered(List<OrderedNode> nodes, int ex) {
		System.out.println(nodes);
		Set<OrderedNode> visited = new HashSet<>();
		assertEquals("missing nodes", ex, nodes.size());
		for (OrderedNode node : nodes) {
			visited.add(node);
			assertTrue("unvisited pred", Iterators.all(g.getPredecessors(node).iterator(), Predicates.in(visited)));
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
