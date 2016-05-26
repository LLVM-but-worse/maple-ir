package org.rsdeob.stdlib.cfg.edge;

import org.rsdeob.stdlib.cfg.BasicBlock;
import org.rsdeob.stdlib.collections.graph.FastGraphEdge;

public abstract class FlowEdge extends FastGraphEdge<BasicBlock> {
		
	public FlowEdge(BasicBlock src, BasicBlock dst) {
		super(src, dst);
	}
	
	public abstract String toGraphString();
	
	@Override
	public abstract String toString();
	
	public abstract String toInverseString();
	
	public abstract FlowEdge clone(BasicBlock src, BasicBlock dst);
}