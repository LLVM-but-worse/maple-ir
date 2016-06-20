package org.rsdeob.stdlib.cfg.edge;

import org.rsdeob.stdlib.collections.graph.FastGraphVertex;
import org.rsdeob.stdlib.collections.graph.flow.ExceptionRange;

public class TryCatchEdge<N extends FastGraphVertex> extends FlowEdge<N> {
	
	public final ExceptionRange<N> erange;
	private int hashcode;
	
	public TryCatchEdge(N src, ExceptionRange<N> erange) {
		super(src, erange.getHandler());
		this.erange = erange;
		recalcHashcode();
	}
	
	private void recalcHashcode() {
		hashcode = 31 + (erange == null ? 0 : erange.hashCode());
		hashcode += (src.getId() + " " + dst.getId()).hashCode();
	}

	@Override
	public String toGraphString() {
		return "Handler";
	}

	@Override
	public String toString() {
		return String.format("TryCatch range: %s -> handler: %s (%s)", src/*ExceptionRange.rangetoString(erange.get())*/, dst.getId(), erange.getTypes());
	}

	@Override
	public String toInverseString() {
		return String.format("TryCatch handler: %s <- range: %s from %s (%s)", dst.getId(), erange.get().iterator().next()/*ExceptionRange.rangetoString(erange.get())*/, src.getId(), erange.getTypes());
	}

	@Override
	public TryCatchEdge<N> clone(N src, N dst) {
		return new TryCatchEdge<N>(src, erange);
	}

	@Override
	public int hashCode() {
		return hashcode;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TryCatchEdge<?> other = (TryCatchEdge<?>) obj;
		if (erange == null) {
			if (other.erange != null)
				return false;
		} else if (!erange.equals(other.erange))
			return false;
		return true;
	}
}