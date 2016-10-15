package org.mapleir.stdlib;

import org.mapleir.stdlib.collections.NodeTable;
import org.objectweb.asm.tree.ClassNode;

public interface IContext {

	NodeTable<ClassNode> getNodes();
}