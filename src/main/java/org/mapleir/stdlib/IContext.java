package org.mapleir.stdlib;

import org.mapleir.stdlib.cfg.ControlFlowGraph;
import org.mapleir.stdlib.collections.NodeTable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public interface IContext {

	NodeTable<ClassNode> getNodes();
	
	ControlFlowGraph createControlFlowGraph(MethodNode m);
}