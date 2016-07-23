package org.mapleir.stdlib.collections;

import java.util.HashMap;
import java.util.Iterator;

import org.objectweb.asm.tree.ClassNode;

public class NodeTable<T extends ClassNode> extends HashMap<String, T> implements Iterable<T> {
	private static final long serialVersionUID = -1402515455164855815L;

	@Override
	public T get(Object key) {
		if (key instanceof ClassNode)
			return super.get(((ClassNode) key).name);
		return super.get(key);
	}

	@Override
	public Iterator<T> iterator() {
		return values().iterator();
	}
}