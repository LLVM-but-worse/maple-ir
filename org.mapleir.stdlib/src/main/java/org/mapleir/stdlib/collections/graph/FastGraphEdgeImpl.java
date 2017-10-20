package org.mapleir.stdlib.collections.graph;

public class FastGraphEdgeImpl<N extends FastGraphVertex> implements FastGraphEdge<N> {
	protected final N src, dst;

	public FastGraphEdgeImpl(N src, N dst) {
		this.src = src;
		this.dst = dst;
	}

	@Override
	public N src() {
		return src;
	}

	@Override
	public N dst() {
		return dst;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null)
			return false;
		if (this == o)
			return true;
		if (!(o instanceof FastGraphEdge))
			return false;
		FastGraphEdge v = (FastGraphEdge) o;

		// assert id/numericId are consistent
		assert ((v.src().getNumericId() == src.getNumericId()) == (v.src().getDisplayName().equals(src.getDisplayName())));
		assert ((v.dst().getNumericId() == dst.getNumericId()) == (v.dst().getDisplayName().equals(dst.getDisplayName())));

		return v.src().getNumericId() == src.getNumericId() && v.dst().getNumericId() == dst.getNumericId();
	}

	@Override
	public int hashCode() {
		int result = src.getNumericId();
		result = 31 * result + dst.getNumericId();
		return result;
	}
}
