package org.mapleir.deobimpl2.cxt;

import java.util.Set;

import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.stdlib.collections.KeyedValueCreator;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.objectweb.asm.tree.MethodNode;

public class IRCache extends NullPermeableHashMap<MethodNode, ControlFlowGraph> {
	private static final long serialVersionUID = 1L;
	
	public IRCache(KeyedValueCreator<MethodNode, ControlFlowGraph> creator) {
		super(creator);
	}
	
	public IRCache() {
		this(ControlFlowGraphBuilder::build);
	}
	
	public ControlFlowGraph getFor(MethodNode m) {
		return getNonNull(m);
	}
	
	public Set<MethodNode> getActiveMethods() {
		return keySet();
	}
}
