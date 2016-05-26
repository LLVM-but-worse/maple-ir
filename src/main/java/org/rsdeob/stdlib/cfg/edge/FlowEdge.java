package org.rsdeob.stdlib.cfg.edge;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.collections.graph.FastGraphEdge;

public abstract class FlowEdge extends FastGraphEdge<BasicBlock> {
	
	private final InverseFlowEdge inverse;
	
	public FlowEdge(BasicBlock src, BasicBlock dst, InverseFlowEdge inverse) {
		super(src, dst);
		this.inverse = inverse;
	}
	
	protected FlowEdge(BasicBlock src, BasicBlock dst) {
		this(src, dst, null);
	}
	
	public FlowEdge getInverse() {
		return inverse;
	}
	
	public abstract String toGraphString();
	
	@Override
	public abstract String toString();
	
	public abstract FlowEdge clone(BasicBlock src, BasicBlock dst);
}