package org.rsdeob.stdlib.cfg.edge;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ExceptionRange;

public class TryCatchEdge extends FlowEdge {
	
	public final ExceptionRange erange;
	private int hashcode;
	
	public TryCatchEdge(BasicBlock src, ExceptionRange erange) {
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
		return String.format("TryCatch range: %s -> handler: %s (%s)", ExceptionRange.rangetoString(erange.getBlocks()), dst.getId(), erange.getTypes());
	}

	@Override
	public String toInverseString() {
		return String.format("TryCatch handler: %s <- range: %s from %s (%s)", dst.getId(), ExceptionRange.rangetoString(erange.getBlocks()), src.getId(), erange.getTypes());
	}

	@Override
	public TryCatchEdge clone(BasicBlock src, BasicBlock dst) {
		return new TryCatchEdge(src, erange);
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
		TryCatchEdge other = (TryCatchEdge) obj;
		if (erange == null) {
			if (other.erange != null)
				return false;
		} else if (!erange.equals(other.erange))
			return false;
		return true;
	}
}