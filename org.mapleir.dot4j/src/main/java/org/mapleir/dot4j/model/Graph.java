package org.mapleir.dot4j.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.mapleir.dot4j.attr.Attributed;
import org.mapleir.dot4j.attr.Attrs;
import org.mapleir.dot4j.attr.SimpleAttributed;

public class Graph implements Source<Graph>, Target, Connected {

	protected boolean strict, directed, cluster;
	protected String name;
	protected final Set<Node> nodes;
	protected final Set<Graph> subgraphs;
	protected final List<Edge> edges;
	protected final Attributed<Graph> nodeAttrs, edgeAttrs, graphAttrs;
	
	Graph() {
		this(false, false, false, "", new LinkedHashSet<>(), new LinkedHashSet<>(), new ArrayList<>(), null, null, null);
		Context.current().ifPresent(ctx -> {
			getGraphAttr().with(ctx.graphAttrs());
		});
	}
	
	public Graph(boolean strict, boolean directed, boolean cluster, String name, Set<Node> nodes,
			Set<Graph> subgraphs, List<Edge> edges, Attrs nodeAttrs,
			Attrs edgeAttrs, Attrs graphAttrs) {
		this.strict = strict;
		this.directed = directed;
		this.cluster = cluster;
		this.name = name;
		this.nodes = nodes;
		this.subgraphs = subgraphs;
		this.edges = edges;
		this.nodeAttrs = new SimpleAttributed<>(this, nodeAttrs);
		this.edgeAttrs = new SimpleAttributed<>(this, edgeAttrs);
		this.graphAttrs = new SimpleAttributed<>(this, graphAttrs);
	}
	
	public Graph copy() {
		return new Graph(strict, directed, cluster, name, new LinkedHashSet<>(nodes), new LinkedHashSet<>(subgraphs),
				new ArrayList<>(edges), nodeAttrs, edgeAttrs, graphAttrs);
	}

	public Graph addEdges(Target... targets) {
		for(Target target : targets) {
			addEdge(target);
		}
		return this;
	}
	
	@Override
	public Graph addEdge(Target target) {
		Edge edge = target.edgeToHere();
		edges.add(Edge.between(this, edge.getTarget()).with(edge.getAttrs()));
		return this;
	}

	@Override
	public Edge edgeToHere() {
		return Edge.to(this);
	}

	@Override
	public Collection<Edge> getEdges() {
		return edges;
	}

	public Graph setStrict(boolean strict) {
		this.strict = strict;
		return this;
	}

	public Graph setDirected(boolean directed) {
		this.directed = directed;
		return this;
	}

	public Graph setClustered(boolean cluster) {
		this.cluster = cluster;
		return this;
	}

	public Graph setName(String name) {
		this.name = name;
		return this;
	}

	public Graph addSource(Source<?> source) {
		if(source instanceof Node) {
			nodes.add((Node)source);
			return this;
		}
		if(source instanceof PortNode) {
			nodes.add(((PortNode) source).getNode());
			return this;
		}
		if(source instanceof Graph) {
			subgraphs.add((Graph) source);
			return this;
		}
		throw new IllegalArgumentException("Unknown source of type " + source.getClass());
	}

	public Graph addSources(Source<?>... sources) {
		for(Source<?> source : sources) {
			addSource(source);
		}
		return this;
	}
	
	public Collection<Node> getRootNodes() {
		return nodes;
	}
	
	public Collection<Node> getAllNodes() {
		Set<Node> set = new HashSet<>();
		for(Node n : nodes) {
			addNodes(n, set);
		}
		return set;
	}
	
	private void addNodes(Node node, Set<Node> vis) {
		if(!vis.contains(node)) {
			vis.add(node);
			for(Edge e : node.getEdges()) {
				if(e.getTarget() instanceof PortNode) {
					addNodes(((PortNode) e.getTarget()).getNode(), vis);
				}
			}
		}
	}
	
	public boolean isStrict() {
		return strict;
	}
	
	public boolean isDirected() {
		return directed;
	}
	
	public boolean isClustered() {
		return cluster;
	}
	
	public String getName() {
		return name;
	}
	
	public Attributed<Graph> getNodeAttr() {
		return nodeAttrs;
	}

	public Attributed<Graph> getEdgeAttr() {
		return edgeAttrs;
	}

	public Attributed<Graph> getGraphAttr() {
		return graphAttrs;
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final Graph graph = (Graph) o;
		if (strict != graph.strict) {
			return false;
		}
		if (directed != graph.directed) {
			return false;
		}
		if (cluster != graph.cluster) {
			return false;
		}
		if (!name.equals(graph.name)) {
			return false;
		}
		if (!nodes.equals(graph.nodes)) {
			return false;
		}
		if (!subgraphs.equals(graph.subgraphs)) {
			return false;
		}
		if (!edges.equals(graph.edges)) {
			return false;
		}
		if (!nodeAttrs.equals(graph.nodeAttrs)) {
			return false;
		}
		if (!edgeAttrs.equals(graph.edgeAttrs)) {
			return false;
		}
		return graphAttrs.equals(graph.graphAttrs);
	}

	@Override
	public int hashCode() {
		int result = (strict ? 1 : 0);
		result = 31 * result + (directed ? 1 : 0);
		result = 31 * result + (cluster ? 1 : 0);
		result = 31 * result + name.hashCode();
		result = 31 * result + nodes.hashCode();
		result = 31 * result + subgraphs.hashCode();
		result = 31 * result + edges.hashCode();
		result = 31 * result + nodeAttrs.hashCode();
		result = 31 * result + edgeAttrs.hashCode();
		result = 31 * result + graphAttrs.hashCode();
		return result;
	}
	
	@Override
	public String toString() {
		return new Serialiser(this).serialise();
	}
}
