package org.rsdeob.stdlib.cfg.edge;

import org.rsdeob.stdlib.cfg.BasicBlock;

public abstract class InverseFlowEdge extends FlowEdge {
	
	protected InverseFlowEdge(BasicBlock src, BasicBlock dst) {
		super(src, dst);
	}
	
	@Override
	public FlowEdge getInverse() {
		throw new UnsupportedOperationException("Inverse of inverse? Use the real edge...");
	}
}