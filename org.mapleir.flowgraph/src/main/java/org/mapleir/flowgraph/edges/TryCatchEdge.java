package org.mapleir.flowgraph.edges;

import java.util.List;
import java.util.Objects;

import org.mapleir.flowgraph.ExceptionRange;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;

public class TryCatchEdge<N extends FastGraphVertex> extends AbstractFlowEdge<N> {

	public final ExceptionRange<N> erange;
	
	public TryCatchEdge(N src, N dst) {
		super(TRYCATCH, src, dst);
		this.erange = null;
	}
	
	public TryCatchEdge(N src, ExceptionRange<N> erange) {
		super(TRYCATCH, src, erange.getHandler());
		this.erange = erange;
	}

	@Override
	public String toGraphString() {
		return "Handler";
	}

	@Override
	public String toString() {
		List<N> l = erange.get();
		return String.format("TryCatch range: %s -> %s", erange.toString(), dst.toString());
	}

	@Override
	public String toInverseString() {
		return String.format("TryCatch handler: %s <- range: %s", dst.getDisplayName(), erange.toString());
	}

	@Override
	public TryCatchEdge<N> clone(N src, N dst) {
		return new TryCatchEdge<>(src, erange);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		if (!super.equals(o)) return false;
		TryCatchEdge<?> that = (TryCatchEdge<?>) o;
		return Objects.equals(erange, that.erange);
	}

	@Override
	public int hashCode() {

		return Objects.hash(super.hashCode(), erange);
	}
}
