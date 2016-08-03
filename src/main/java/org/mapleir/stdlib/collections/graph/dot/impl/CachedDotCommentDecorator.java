package org.mapleir.stdlib.collections.graph.dot.impl;

import java.util.List;

import org.mapleir.stdlib.collections.ListCreator;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;

public class CachedDotCommentDecorator<G extends FastGraph<N, E>, N extends FastGraphVertex, E extends FastGraphEdge<N>> extends CommentDecorator<G, N, E> {

	private final NullPermeableHashMap<N, List<String>> startComments;
	private final NullPermeableHashMap<N, List<String>> endComments;
	
	public CachedDotCommentDecorator() {
		startComments = new NullPermeableHashMap<>(new ListCreator<>());
		endComments = new NullPermeableHashMap<>(new ListCreator<>());
	}
	
	public CachedDotCommentDecorator<G, N, E> addStartComment(N n, String l) {
		startComments.getNonNull(n).add(l);
		return this;
	}
	
	public CachedDotCommentDecorator<G, N, E> addEndComment(N n, String l) {
		endComments.getNonNull(n).add(l);
		return this;
	}
	
	public CachedDotCommentDecorator<G, N, E> removeStartComment(N n, String l) {
		startComments.getNonNull(n).remove(l);
		return this;
	}
	
	public CachedDotCommentDecorator<G, N, E> removeEndComment(N n, String l) {
		endComments.getNonNull(n).remove(l);
		return this;
	}
	
	public CachedDotCommentDecorator<G, N, E> clear() {
		startComments.clear();
		endComments.clear();
		return this;
	}
	
	public CachedDotCommentDecorator<G, N, E> clearStartComments() {
		startComments.clear();
		return this;
	}
	
	public CachedDotCommentDecorator<G, N, E> clearEndComments() {
		endComments.clear();
		return this;
	}
	
	@Override
	public List<String> getVertexStartComments(G g, N n) {
		return startComments.get(n);
	}

	@Override
	public List<String> getVertexEndComments(G g, N n) {
		return endComments.get(n);
	}
}
