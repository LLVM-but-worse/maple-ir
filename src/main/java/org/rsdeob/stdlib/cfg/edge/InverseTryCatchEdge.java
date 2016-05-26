package org.rsdeob.stdlib.cfg.edge;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.cfg.ExceptionRange;

public class InverseTryCatchEdge extends InverseFlowEdge {
	
	public final ExceptionRange erange;
	
	protected InverseTryCatchEdge(BasicBlock src, BasicBlock dst, ExceptionRange erange) {
		super(src, dst);
		this.erange = erange;
	}

	@Override
	public String toGraphString() {
		return "TODO";
	}

	@Override
	public String toString() {
		return String.format("TryCatch handler: %s <- range: %s from %s (%s)", dst.getId(), ExceptionRange.rangetoString(erange.getBlocks()), src.getId(), erange.getTypes());
	}

	@Override
	public InverseTryCatchEdge clone(BasicBlock src, BasicBlock dst) {
		return new InverseTryCatchEdge(src, dst, erange);
	}
}