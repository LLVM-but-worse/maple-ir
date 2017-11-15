package org.mapleir.stdlib.collections.graph;

public interface EdgeCloneable<N extends FastGraphVertex, E extends FastGraphEdge<N>> {

	E clone(N src, N dst);
}