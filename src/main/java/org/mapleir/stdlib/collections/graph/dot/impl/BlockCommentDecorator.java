package org.mapleir.stdlib.collections.graph.dot.impl;

import org.mapleir.stdlib.collections.ListCreator;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.graph.dot.DotPropertyDecorator;

import java.util.List;
import java.util.Map;

public class BlockCommentDecorator<G extends FastGraph<N, E>, N extends FastGraphVertex, E extends FastGraphEdge<N>> implements DotPropertyDecorator<G, N, E> {
	private final NullPermeableHashMap<N, List<String>> vertexStartComments;
	private final NullPermeableHashMap<N, List<String>> vertexEndComments;
	
	public BlockCommentDecorator() {
		vertexStartComments = new NullPermeableHashMap<>(new ListCreator<>());
		vertexEndComments = new NullPermeableHashMap<>(new ListCreator<>());
	}
	
	public BlockCommentDecorator<G, N, E> addStartComment(N n, String comment) {
		vertexStartComments.getNonNull(n).add(comment);
		return this;
	}
	
	public BlockCommentDecorator<G, N, E> addEndComment(N n, String comment) {
		vertexEndComments.getNonNull(n).add(comment);
		return this;
	}
	
	public BlockCommentDecorator<G, N, E> clearComments() {
		vertexStartComments.clear();
		vertexEndComments.clear();
		return this;
	}
	
	@Override
	public void decorateNodeProperties(G g, N n, Map<String, Object> nprops) {
		if (nprops.containsKey("label")) {
			StringBuilder sb = new StringBuilder();
			for (String comment : vertexStartComments.getNonNull(n))
				sb.append("// ").append(comment).append("\\l");
			sb.append(nprops.get("label").toString());
			sb.append("\\l");
			for (String comment : vertexEndComments.getNonNull(n))
				sb.append("// ").append(comment).append("\\l");
			nprops.put("label", sb.toString());
		}
	}
}
