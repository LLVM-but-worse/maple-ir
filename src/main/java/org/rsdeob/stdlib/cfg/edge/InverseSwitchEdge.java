package org.rsdeob.stdlib.cfg.edge;

import org.rsdeob.stdlib.cfg.BasicBlock;

public class InverseSwitchEdge extends InverseFlowEdge {
	
	public final int value;
	
	protected InverseSwitchEdge(BasicBlock src, BasicBlock dst, int value) {
		super(src, dst);
		this.value = value;
	}
	@Override
	public String toString() {
		return String.format("Switch[%d] #%s <- #%s", value, src.getId(), dst.getId());
	}
	
	@Override
	public String toGraphString() {
		return "TODO";
	}

	@Override
	public InverseSwitchEdge clone(BasicBlock src, BasicBlock dst) {
		return new InverseSwitchEdge(dst, src, value);
	}
}