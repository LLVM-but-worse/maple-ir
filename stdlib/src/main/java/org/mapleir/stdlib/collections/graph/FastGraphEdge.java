package org.mapleir.stdlib.collections.graph;

public interface FastGraphEdge<N extends FastGraphVertex> extends Comparable<FastGraphEdge<N>> {
	N src();

	N dst();

	@Override
	default int compareTo(FastGraphEdge<N> o) {
		if (this.equals(o))
			return 0;
		else
			return (int) Math.signum(2 * Integer.compare(src().getNumericId(), o.src().getNumericId())
					+ Integer.compare(dst().getNumericId(), o.dst().getNumericId()));
	}
}
