package org.rsdeob.stdlib.cfg;

import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.util.GraphUtils;

public class ControlFlowGraph extends FastBlockGraph {
	
	private final MethodNode method;
	
	ControlFlowGraph(MethodNode method) {
		this.method = method;
	}
	
	public MethodNode getMethod() {
		return method;
	}
	
	@Override
	public String toString() {
		return GraphUtils.toString(this, vertices());
	}
}