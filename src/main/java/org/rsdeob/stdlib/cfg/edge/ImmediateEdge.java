package org.rsdeob.stdlib.cfg.edge;

import org.rsdeob.stdlib.cfg.BasicBlock;

public class ImmediateEdge extends FlowEdge {
	
	public ImmediateEdge(BasicBlock src, BasicBlock dst) {
		super(src, dst);
	}

	@Override
	public String toGraphString() {
		return "Immediate";
	}

	@Override
	public String toString() {
		return String.format("Immediate #%s -> #%s", src.getId(), dst.getId());
	}

	@Override
	public String toInverseString() {
		return String.format("Immediate #%s <- #%s", dst.getId(), src.getId());
	}
	
	@Override
	public ImmediateEdge clone(BasicBlock src, BasicBlock dst) {
		return new ImmediateEdge(src, dst);
	}
}