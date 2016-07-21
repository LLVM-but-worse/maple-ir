package org.mapleir.stdlib.cfg;

import org.mapleir.stdlib.collections.graph.util.GraphUtils;
import org.objectweb.asm.tree.MethodNode;

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