package org.mapleir.context;

import java.util.Set;

import org.objectweb.asm.tree.MethodNode;

public interface ApplicationContext {

	Set<MethodNode> getEntryPoints();
}