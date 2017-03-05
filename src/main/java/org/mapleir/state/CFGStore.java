package org.mapleir.state;

import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.objectweb.asm.tree.MethodNode;

import java.util.Set;

public class CFGStore extends NullPermeableHashMap<MethodNode, ControlFlowGraph> {
	private static final long serialVersionUID = 1L;
	
	public CFGStore() {
		super(ControlFlowGraphBuilder::build);
	}
	
	public ControlFlowGraph getIR(MethodNode m) {
		return getNonNull(m);
	}
	
	public Set<MethodNode> getActiveMethods() {
		return keySet();
	}
}
