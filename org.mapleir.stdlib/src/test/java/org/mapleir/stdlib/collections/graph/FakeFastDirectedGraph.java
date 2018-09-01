package org.mapleir.stdlib.collections.graph;

import org.mapleir.stdlib.collections.graph.FakeFastDirectedGraph.FakeFastEdge;
import org.mapleir.stdlib.collections.graph.FakeFastDirectedGraph.FakeFastVertex;

public class FakeFastDirectedGraph extends FastDirectedGraph<FakeFastVertex, FakeFastEdge> {

	private final FastDirectedGraphTest test;
	
	public FakeFastDirectedGraph(FastDirectedGraphTest test) {
		this.test = test;
	}
	
	@Override
	public FakeFastEdge clone(FakeFastEdge e, FakeFastVertex oldN, FakeFastVertex newN) {
		FakeFastVertex src = e.src();
		FakeFastVertex dst = e.dst();

		if (src == oldN) {
			src = newN;
		}

		if (dst == oldN) {
			dst = newN;
		}
		
		return new FakeFastEdge(src, dst);
	}
	
	public static class FakeFastEdge extends FastGraphEdgeImpl<FakeFastVertex> {
		public FakeFastEdge(FakeFastVertex src, FakeFastVertex dst) {
			super(src, dst);
		}
		
		@Override
		public String toString() {
			return src.getDisplayName() + " -> " + dst.getDisplayName();
		}
	}
	
	public static class FakeFastVertex implements FastGraphVertex {
		private final int id;
		
		public FakeFastVertex(int id) {
			this.id = id;
		}

		@Override
		public int getNumericId() {
			return id;
		}

		@Override
		public String getDisplayName() {
			return String.valueOf(id);
		}
	}
}
