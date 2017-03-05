package org.mapleir.state;

import org.mapleir.stdlib.klass.ClassHelper;
import org.objectweb.asm.tree.ClassNode;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public abstract class ClassSource {

	protected final Map<String, ClassNode> nodeMap;
	
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
	
	protected void rebuildTable() {
		Set<ClassNode> cset = new HashSet<>();
		cset.addAll(nodeMap.values());
		nodeMap.clear();
		
		for(ClassNode cn : cset) {
			nodeMap.put(cn.name, cn);
		}
	}
	
	public Iterable<ClassNode> iterate() {
		return this::iterator;
	}
	
	public Iterator<ClassNode> iterator() {
		return nodeMap.values().iterator();
	}
}