package org.rsdeob.stdlib;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.rsdeob.stdlib.cfg.ControlFlowGraph;
import org.rsdeob.stdlib.collections.NodeTable;

public interface IContext {

	NodeTable<ClassNode> getNodes();
	
	ControlFlowGraph createControlFlowGraph(MethodNode m);
}