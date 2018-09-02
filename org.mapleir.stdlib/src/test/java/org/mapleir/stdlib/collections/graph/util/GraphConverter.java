package org.mapleir.stdlib.collections.graph.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.mapleir.dot4j.model.Edge;
import org.mapleir.dot4j.model.Graph;
import org.mapleir.dot4j.model.Node;
import org.mapleir.dot4j.model.PortNode;
import org.mapleir.dot4j.parse.Parser;
import org.mapleir.stdlib.collections.graph.FastGraph;

public class GraphConverter {

	public static FastGraph<OrderedNode, OrderedNode.ONEdge> fromFile(String name) throws IOException {
		return fromDot(Parser.read(GraphConverter.class.getResourceAsStream(name)));
	}
	
	public static FastGraph<OrderedNode, OrderedNode.ONEdge> fromDot(Graph graph) {		
		FastGraph<OrderedNode, OrderedNode.ONEdge> fg;
		if(graph.isDirected()) {
			fg = new OrderedNode.ODirectedGraph();
		} else {
			fg = new OrderedNode.OUndirectedGraph();
		}
		Map<Node, OrderedNode> mapping = new HashMap<>();
		for(Node node : graph.getAllNodes()) {
			int id = Integer.parseInt(node.getName().toString());
			OrderedNode oNode = new OrderedNode(id);
			mapping.put(node, oNode);
			fg.addVertex(oNode);
		}
		
		for(Node node : graph.getAllNodes()) {
			for(Edge e : node.getEdges()) {
				OrderedNode src = mapping.get(node);
				OrderedNode dst = mapping.get(((PortNode)e.getTarget()).getNode());
				fg.addEdge(new OrderedNode.ONEdge(src, dst));
			}
		}
		
		return fg;
	}
}
