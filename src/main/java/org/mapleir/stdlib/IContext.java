package org.mapleir.stdlib;

import java.util.Set;

import org.mapleir.ir.cfg.ControlFlowGraph;
import org.mapleir.stdlib.klass.InvocationResolver;
import org.mapleir.stdlib.klass.library.ApplicationClassSource;
import org.objectweb.asm.tree.MethodNode;

public interface IContext {

	ApplicationClassSource getApplication();
	
	InvocationResolver getInvocationResolver();
	
	ControlFlowGraph getIR(MethodNode m);
	
	Set<MethodNode> getActiveMethods();
}