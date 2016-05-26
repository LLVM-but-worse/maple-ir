package org.rsdeob.stdlib.cfg;

import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.util.GraphUtils;
import org.rsdeob.stdlib.collections.graph.FastBlockGraph;

public class ControlFlowGraph extends FastBlockGraph {
	
	private final MethodNode method;
	private RootStatement root;
	
	public ControlFlowGraph(MethodNode method) {
		this.method = method;
	}
	
	public MethodNode getMethod() {
		return method;
	}
	
	public void setRoot(RootStatement root) {
		this.root = root;
	}
	
	public RootStatement getRoot() {
		return root;
	}
	
	@Override
	public String toString() {
		return GraphUtils.toString(this, vertices());
	}
}