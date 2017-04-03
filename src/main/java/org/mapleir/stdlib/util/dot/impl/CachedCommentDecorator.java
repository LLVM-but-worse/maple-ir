package org.mapleir.stdlib.util.dot.impl;

import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.map.ListCreator;
import org.mapleir.stdlib.collections.map.NullPermeableHashMap;

import java.util.List;

public class CachedCommentDecorator<G extends FastGraph<N, E>, N extends FastGraphVertex, E extends FastGraphEdge<N>> extends CommentDecorator<G, N, E> {

	private final NullPermeableHashMap<N, List<String>> startComments;
	private final NullPermeableHashMap<N, List<String>> endComments;
	
	public CachedCommentDecorator() {
		startComments = new NullPermeableHashMap<>(new ListCreator<>());
		endComments = new NullPermeableHashMap<>(new ListCreator<>());
	}
	
	public CachedCommentDecorator<G, N, E> addStartComment(N n, String l) {
		startComments.getNonNull(n).add(l);
		return this;
	}
	
	public CachedCommentDecorator<G, N, E> addEndComment(N n, String l) {
		endComments.getNonNull(n).add(l);
		return this;
	}
	
	public CachedCommentDecorator<G, N, E> removeStartComment(N n, String l) {
		startComments.getNonNull(n).remove(l);
		return this;
	}
	
	public CachedCommentDecorator<G, N, E> removeEndComment(N n, String l) {
		endComments.getNonNull(n).remove(l);
		return this;
	}
	
	public CachedCommentDecorator<G, N, E> clear() {
		startComments.clear();
		endComments.clear();
		return this;
	}
	
	public CachedCommentDecorator<G, N, E> clearStartComments() {
		startComments.clear();
		return this;
	}
	
	public CachedCommentDecorator<G, N, E> clearEndComments() {
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
