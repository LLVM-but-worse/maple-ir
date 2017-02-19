package org.mapleir.stdlib.klass.library;

import java.io.IOException;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

public class InstalledRuntimeClassSource extends LibraryClassSource {

	public InstalledRuntimeClassSource(ApplicationClassSource parent) {
		super(parent);
	}
	
	@Override
	public boolean contains(String name) {
		if(super.contains(name)) {
			return true;
		}
		
		try {
			Class.forName(name.replace("/", "."));
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	@Override
	protected LocateableClassNode findClass0(String name) {
		LocateableClassNode node = super.findClass0(name);
		if(node != null) {
			return node;
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
			System.err.println(e.getMessage());
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