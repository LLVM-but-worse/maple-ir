package org.mapleir.stdlib.cfg;

import java.util.Set;

import org.mapleir.stdlib.collections.bitset.GenericBitSet;
import org.mapleir.stdlib.collections.graph.util.GraphUtils;
import org.objectweb.asm.tree.MethodNode;

public class ControlFlowGraph extends FastBlockGraph {
	
	private final MethodNode method;
	
	public ControlFlowGraph(MethodNode method) {
		this.method = method;
	}
	
	public ControlFlowGraph(ControlFlowGraph cfg) {
		super(cfg);
		method = cfg.method;
	}
	
	public MethodNode getMethod() {
		return method;
	}
	
	@Override
	public String toString() {
		return GraphUtils.toString(this, vertices());
	}
	
	@Override
	public ControlFlowGraph copy() {
		return new ControlFlowGraph(this);
	}
	
	public Set<BasicBlock> createBitSet() {
		return new GenericBitSet<>(indexer);
	}
}