package org.mapleir.context;

import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.ir.cfg.builder.ControlFlowGraphBuilder;
import org.mapleir.stdlib.collections.map.KeyedValueCreator;
import org.mapleir.stdlib.collections.map.NullPermeableHashMap;
import org.mapleir.stdlib.util.JavaDesc;
import org.objectweb.asm.tree.MethodNode;

import java.util.Set;

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

	public MethodNode findMethod(JavaDesc jd) {
		return getActiveMethods().stream().filter(mn -> mn.getJavaDesc().equals(jd)).findFirst().orElseGet(null);
	}
	
	public Set<MethodNode> getActiveMethods() {
		return keySet();
	}
}
