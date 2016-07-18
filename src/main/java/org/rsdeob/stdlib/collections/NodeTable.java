package org.rsdeob.stdlib.collections;

import org.objectweb.asm.tree.ClassNode;

import java.util.HashMap;

public class NodeTable<T extends ClassNode> extends HashMap<String, T> {
	private static final long serialVersionUID = -1402515455164855815L;

	@Override
	public T get(Object key) {
		if (key instanceof ClassNode)
			return super.get(((ClassNode) key).name);
		return super.get(key);
	}
}