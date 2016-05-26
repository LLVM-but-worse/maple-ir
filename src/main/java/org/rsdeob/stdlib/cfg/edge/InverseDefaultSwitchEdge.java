package org.rsdeob.stdlib.cfg.edge;

import org.rsdeob.stdlib.cfg.BasicBlock;

public class InverseDefaultSwitchEdge extends InverseFlowEdge {
	
	protected InverseDefaultSwitchEdge(BasicBlock src, BasicBlock dst) {
		super(src, dst);
	}

	@Override
	public String toString() {
		return String.format("Default Switch #%s <- #%s", src.getId(), dst.getId());
	}
	
	@Override
	public String toGraphString() {
		return "Default";
	}

	@Override
	public FlowEdge clone(BasicBlock src, BasicBlock dst) {
		return new InverseDefaultSwitchEdge(dst, src);
	}
}