package org.rsdeob.stdlib.cfg.edge;

import org.rsdeob.stdlib.cfg.BasicBlock;

public class InverseImmediateEdge extends InverseFlowEdge {

	protected InverseImmediateEdge(BasicBlock src, BasicBlock dst) {
		super(src, dst);
	}

	@Override
	public String toString() {
		return String.format("Immediate #%s <- #%s", src.getId(), dst.getId());
	}
	
	@Override
	public String toGraphString() {
		return "TODO";
	}

	@Override
	public InverseImmediateEdge clone(BasicBlock src, BasicBlock dst) {
		return new InverseImmediateEdge(dst, src);
	}
}