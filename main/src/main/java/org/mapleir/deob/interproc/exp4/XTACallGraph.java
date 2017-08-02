package org.mapleir.deob.interproc.exp4;

import java.util.Set;

import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.collections.map.CachedKeyedValueCreator.DelegatingCachedKeyedValueCreator;
import org.mapleir.stdlib.collections.map.KeyedValueCreator;
import org.mapleir.stdlib.collections.map.SetCreator;

public class XTACallGraph extends FastDirectedGraph<Node, FastGraphEdge<Node>>{

	private final KeyedValueCreator<Node, Set<Node>> allocSetCreator = new DelegatingCachedKeyedValueCreator<>(new SetCreator<>());
	
	public void build() {
		
	}
	
	public Set<Node> getAllocTypes(Node n) {
		return allocSetCreator.create(n);
	}
}