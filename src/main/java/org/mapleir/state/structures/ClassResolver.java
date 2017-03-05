package org.mapleir.state.structures;

import org.mapleir.state.ApplicationClassSource;
import org.mapleir.state.LocateableClassNode;
import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.ValueCreator;
import org.objectweb.asm.tree.ClassNode;

import java.util.LinkedHashSet;
import java.util.Set;

public class ClassResolver {
	private static final ValueCreator<Set<ClassNode>> SET_CREATOR = LinkedHashSet::new;
	
	private final ApplicationClassSource source;
	private final NullPermeableHashMap<ClassNode, Set<ClassNode>> supers;
	private final NullPermeableHashMap<ClassNode, Set<ClassNode>> delgates;
	
	public ClassResolver(ApplicationClassSource source) {
		this.source = source;
		supers   = new NullPermeableHashMap<>(SET_CREATOR);
		delgates = new NullPermeableHashMap<>(SET_CREATOR);
		
		build();
	}
	
	protected ClassNode findClass(String name) {
		LocateableClassNode n = source.findClass(name);
		if(n != null) {
			ClassNode cn = n.node;
			return cn;
		} else {
			throw new RuntimeException(String.format("Class not found %s", name));
		}
	}
	
	protected void build() {
		for (ClassNode node : source.iterateWithLibraries()) {
			build(node);
		}
	}
	
	protected void build(ClassNode cn) {
		if(cn.superName != null) {
			ClassNode sup = findClass(cn.superName);
			supers.getNonNull(cn).add(sup);
			delgates.getNonNull(sup).add(cn);
		} else {
			ClassNode sup = findClass("java/lang/Object");
			supers.getNonNull(cn).add(sup);
			delgates.getNonNull(sup).add(cn);
		}
		
		for(String s : cn.interfaces) {
			ClassNode iface = findClass(s);
			
			supers.getNonNull(cn).add(iface);
			delgates.getNonNull(iface).add(cn);
		}
	}
	
	public Set<ClassNode> getParents(ClassNode cn) {
		return supers.get(cn);
	}
	
	public Set<ClassNode> getChildren(ClassNode cn) {
		return delgates.get(cn);
	}
}