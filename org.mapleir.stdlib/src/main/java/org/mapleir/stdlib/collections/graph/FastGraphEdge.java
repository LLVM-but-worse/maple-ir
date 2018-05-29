package org.mapleir.stdlib.collections.graph;

public interface FastGraphEdge<N extends FastGraphVertex> extends Comparable<FastGraphEdge<N>> {
	N src();

	N dst();

	// WHY DOES THIS EXIST?!
	@Override
	default int compareTo(FastGraphEdge<N> o) {
		if (this.equals(o))
			return 0;
		else {
			int result = Integer.compare(src().getNumericId(), o.src().getNumericId());
			if (result == 0)
				result = Integer.compare(dst().getNumericId(), o.dst().getNumericId());
			assert(result != 0);
			return result;
		}
	}
}
