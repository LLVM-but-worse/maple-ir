package org.mapleir.stdlib.ir.gen.interference;

import org.mapleir.stdlib.collections.graph.FastGraphEdge;

public class InterferenceEdge extends FastGraphEdge<ColourableNode> implements Comparable<InterferenceEdge> {
	
	public InterferenceEdge(ColourableNode src, ColourableNode dst) {
		super(src, dst);
	}
	
	@Override
	public String toString() {
		return dst.getLocal().toString();
	}

	@Override
	public int compareTo(InterferenceEdge o) {
		return src.getLocal().compareTo(dst.getLocal());
	}
}