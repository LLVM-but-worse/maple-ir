package org.mapleir.state.structures;

import org.mapleir.state.ApplicationClassSource;
import org.mapleir.state.LocateableClassNode;
import org.objectweb.asm.tree.ClassNode;

public class ClassResolver {
	private final ApplicationClassSource source;
	
	public ClassResolver(ApplicationClassSource source) {
		this.source = source;
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
}