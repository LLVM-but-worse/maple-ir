package org.mapleir.stdlib.collections.graph;

import java.util.Collection;
import java.util.Iterator;
import java.util.function.BiConsumer;

import org.mapleir.dot4j.attr.Attrs;
import org.mapleir.dot4j.attr.builtin.Font;
import org.mapleir.dot4j.model.Context;
import org.mapleir.dot4j.model.Edge;
import org.mapleir.dot4j.model.Factory;
import org.mapleir.dot4j.model.Graph;
import org.mapleir.dot4j.model.Node;

public class GraphUtils {
	
	private static final Attrs DEFAULT_FONT = Font.config("consolas bold", 8.0D, 200.0D);
	
	public static Attrs getDefaultFont() {
		return Attrs.attrs(DEFAULT_FONT);
	}
	
	public static <N extends FastGraphVertex> Graph makeGraphSkeleton(FastGraph<N, ? extends FastGraphEdge<N>> fastGraph) {
		return makeGraphSkeleton(fastGraph, null, null);
	}
	
	public static <N extends FastGraphVertex, E extends FastGraphEdge<N>> Graph makeGraphSkeleton(FastGraph<N, E> fastGraph,
			BiConsumer<N, Node> nodeConsumer, BiConsumer<E, Edge> edgeConsumer) {
		Attrs font = GraphUtils.getDefaultFont();
		Graph graph = Factory.graph()
				.getGraphAttr().with(font)
				.getNodeAttr().with(font)
				.getEdgeAttr().with(font);
		
		return Context.use(graph, ctx -> {
			for(N vertex : fastGraph.vertices()) {
				Node sourceNode = Factory.node(vertex.getDisplayName());
				graph.addSource(sourceNode);
				if(nodeConsumer != null) {
					nodeConsumer.accept(vertex, sourceNode);
				}
				
				for(E e : fastGraph.getEdges(vertex)) {
					Node target = Factory.node(e.dst().getDisplayName());
					Edge edge = Factory.to(target);
					if(edgeConsumer != null) {
						edgeConsumer.accept(e, edge);
					}
					sourceNode.addEdge(edge);
				}
			}
			return graph;
		});
	}
	
	public static <N extends FastGraphVertex> String toNodeArray(Collection<N> col) {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		Iterator<N> it = col.iterator();
		while(it.hasNext()) {
			N b = it.next();
			if(b == null) {
				sb.append("null");
			} else {
				sb.append(b.getDisplayName());
			}
			
			if(it.hasNext()) {
				sb.append(", ");
			}
		}
		sb.append("]");
		return sb.toString();
	}

	public static int getEdgeCount(FastGraph<FastGraphVertex, ?> g) {
		int c = 0;		
		for(FastGraphVertex v : g.vertices()) {
			c += g.getEdges(v).size();
		}
		return c;
	}

	@Deprecated
	public static <N extends FastGraphVertex, E extends FastGraphEdge<N>> int compareEdgesById(E a, E b) {
		if (a.equals(b))
			return 0;
		else {
			int result = Integer.compare(a.src().getNumericId(), b.src().getNumericId());
			if (result == 0)
				result = Integer.compare(a.dst().getNumericId(), b.dst().getNumericId());
			assert (result != 0);
			return result;
		}
	}
}
