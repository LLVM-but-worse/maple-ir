package org.rsdeob.stdlib;

import org.objectweb.asm.tree.ClassNode;
import org.rsdeob.stdlib.collections.NodeTable;

public interface IContext {

	NodeTable<ClassNode> getNodes();
}