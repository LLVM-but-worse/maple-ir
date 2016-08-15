package org.mapleir.stdlib.collections.graph.dot.impl;

import java.util.ArrayList;
import java.util.List;

import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.ir.transform.Liveness;

public class LivenessDecorator<G extends FastGraph<N, E>, N extends FastGraphVertex, E extends FastGraphEdge<N>> extends CommentDecorator<G, N, E> {
	
	private Liveness<N> liveness;
	
	public LivenessDecorator<G, N, E> setLiveness(Liveness<N> liveness) {
		this.liveness = liveness;
		return this;
	}
	
	@Override
	public List<String> getVertexStartComments(G g, N n) {
		List<String> list = new ArrayList<>();
		list.add("IN: " + liveness.in(n));
		return list;
	}

	@Override
	public List<String> getVertexEndComments(G g, N n) {
		List<String> list = new ArrayList<>();
		list.add("OUT: " + liveness.out(n));
		return list;
	}
}
