package org.mapleir.stdlib.klass.library;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class InstalledRuntimeClassSource extends LibraryClassSource {

	public InstalledRuntimeClassSource(ApplicationClassSource parent) {
		super(parent);
	}
	
	private static Map<String, Boolean> containsCache = new HashMap<>();
	
	@Override
	public boolean contains(String name) {
		Boolean cached = containsCache.get(name);
		if (cached != null)
			return cached;
		
		if(super.contains(name)) {
			containsCache.put(name, true);
			return true;
		}
		
		try {
			Class.forName(name.replace("/", "."));
			containsCache.put(name, true);
			return true;
		} catch (ClassNotFoundException e) {
			containsCache.put(name, false);
			return false;
		}
	}

	@Override
	protected LocateableClassNode findClass0(String name) {
		LocateableClassNode node = super.findClass0(name);
		if(node != null) {
			return node;
		}
		
		if(name.startsWith("[")) {
			return findClass0("java/lang/Object");
		}
		
		try {
			ClassReader cr = new ClassReader(name);
			ClassNode cn = new ClassNode();
			cr.accept(cn, 0);
			nodeMap.put(cn.name, cn);
			
			node = new LocateableClassNode(this, cn);
			return node;
		} catch(IOException e) {
			// TODO: logger
			System.err.println(e.getMessage() + ": " + name);
			return null;
		}
	}
	
	@Override
	public boolean isIterable() {
		return false;
	}
	
	@Override
	public String toString() {
		return System.getProperty("java.version");
	}
}