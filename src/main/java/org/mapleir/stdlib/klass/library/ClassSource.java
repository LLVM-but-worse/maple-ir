package org.mapleir.stdlib.klass.library;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.mapleir.stdlib.klass.ClassHelper;
import org.objectweb.asm.tree.ClassNode;

public abstract class ClassSource {

	protected final Map<String, ClassNode> nodeMap;
	
	public ClassSource() {
		this(new HashMap<>());
	}
	
	public ClassSource(Collection<ClassNode> classes) {
		this(ClassHelper.convertToMap(classes));
	}
	
	public ClassSource(Map<String, ClassNode> nodeMap) {
		this.nodeMap = nodeMap;
	}
	
	public boolean contains(String name) {
		if(name == null) {
			return false;
		}
		
		return nodeMap.containsKey(name);
	}
	
	/* internal method to look up a class in the current pool.*/
	protected LocateableClassNode findClass0(String name) {
		if(contains(name)) {
			ClassNode node = nodeMap.get(name);
			if(node != null) {
				return new LocateableClassNode(this, node);
			}
		}
		return null;
	}
	
	protected static void throwNoParent() {
		throw new UnsupportedOperationException("Null parent.");
	}
	
	public Iterable<ClassNode> iterate() {
		return new Iterable<ClassNode>() {
			@Override
			public Iterator<ClassNode> iterator() {
				return ClassSource.this.iterator();
			}
		};
	}
	
	public Iterator<ClassNode> iterator() {
		return nodeMap.values().iterator();
	}
}