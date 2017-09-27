package org.mapleir.stdlib.collections.graph.dot.impl;

import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.collections.graph.dot.DotPropertyDecorator;

import java.util.List;
import java.util.Map;

public abstract class CommentDecorator<G extends FastGraph<N, E>, N extends FastGraphVertex, E extends FastGraphEdge<N>> implements DotPropertyDecorator<G, N, E> {
	
	public abstract List<String> getVertexStartComments(G g, N n);
	
	public abstract List<String> getVertexEndComments(G g, N n);
	
	@Override
	public void decorateNodeProperties(G g, N n, Map<String, Object> nprops) {
		if (nprops.containsKey("label")) {
			StringBuilder sb = new StringBuilder();
			
			List<String> startComments = getVertexStartComments(g, n);
			if(startComments != null) {
				for (String comment : startComments) {
					sb.append("// ").append(comment).append("\\l");
				}
			}
			
			sb.append(nprops.get("label").toString());
			sb.append("\\l");
			
			List<String> endComments = getVertexEndComments(g, n);
			if(endComments != null) {
				for (String comment : endComments) {
					sb.append("// ").append(comment).append("\\l");
				}
			}
			
			nprops.put("label", sb.toString());
		}
	}
}
