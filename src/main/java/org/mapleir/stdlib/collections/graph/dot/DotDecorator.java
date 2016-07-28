package org.mapleir.stdlib.collections.graph.dot;

import org.mapleir.stdlib.collections.ListCreator;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DotDecorator<N extends FastGraphVertex> {
	private final NullPermeableHashMap<N, List<String>> vertexStartComments;
	private final NullPermeableHashMap<N, List<String>> vertexEndComments;
	private final Map<N, String> vertexColors;
	
	public DotDecorator() {
		vertexStartComments = new NullPermeableHashMap<>(new ListCreator<>());
		vertexEndComments = new NullPermeableHashMap<>(new ListCreator<>());
		vertexColors = new HashMap<>();
	}
	
	public DotDecorator<N> addStartComment(N n, String comment) {
		vertexStartComments.getNonNull(n).add(comment);
		return this;
	}
	
	public DotDecorator<N> addEndComment(N n, String comment) {
		vertexEndComments.getNonNull(n).add(comment);
		return this;
	}
	
	public DotDecorator<N> setColor(N n, String color) {
		vertexColors.put(n, color);
		return this;
	}
	
	public void decorateNodeProperties(N n, Map<String, Object> nprops) {
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
		if (vertexColors.containsKey(n)) {
			nprops.put("style", "filled");
			nprops.put("fillcolor", vertexColors.get(n));
		}
	}
}
