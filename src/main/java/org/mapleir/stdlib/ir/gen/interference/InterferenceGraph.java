package org.mapleir.stdlib.ir.gen.interference;

import org.mapleir.stdlib.collections.graph.FastUndirectedGraph;

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

public class InterferenceGraph extends FastUndirectedGraph<ColourableNode, InterferenceEdge> {

	public InterferenceGraph() {
		
	}
	
	@Override
	public boolean excavate(ColourableNode n) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean jam(ColourableNode pred, ColourableNode succ, ColourableNode n) {
		throw new UnsupportedOperationException();
	}

	@Override
	public InterferenceEdge clone(InterferenceEdge edge, ColourableNode oldN, ColourableNode newN) {
		return new InterferenceEdge(oldN, newN);
	}
		
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("map {\n");
		for(Entry<ColourableNode, Set<InterferenceEdge>> e : new HashSet<>(map.entrySet())) {
			sb.append("   ").append(e.getKey()).append(" interferes with ").append(e.getValue()).append("\n");
		}
		sb.append("}");
		return sb.toString();
	}

	@Override
	public InterferenceEdge invert(InterferenceEdge edge) {
		return new InterferenceEdge(edge.dst, edge.src);
	}
}