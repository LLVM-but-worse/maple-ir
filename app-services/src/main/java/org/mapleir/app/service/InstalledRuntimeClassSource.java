package org.mapleir.app.service;

import java.io.IOException;
import java.util.HashSet;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

public class InstalledRuntimeClassSource extends LibraryClassSource {

	private final HashSet<String> notContains;
	
	public InstalledRuntimeClassSource(ApplicationClassSource parent) {
		super(parent);
		notContains = new HashSet<>();
	}
	
	@Override
	public boolean contains(String name) {
		if(super.contains(name)) {
			return true;
		} else if(!notContains.contains(name)) {
			return __resolve(name) != null;
		} else {
			return false;
		}
	}

	@Override
	protected LocateableClassNode findClass0(String name) {
		/* check the cache first. */
		LocateableClassNode node = super.findClass0(name);
		if(node != null) {
			return node;
		}
		
		return __resolve(name);
	}
	
	/* (very) internal class loading method. doesn't
	 * poll cache at all. */
	private LocateableClassNode __resolve(String name) {
		if(name.startsWith("[")) {
			/* calling Object methods. (clone() etc)
			 * that we haven't already resolved.
			 * 
			 * Cache it so that contains() can
			 * quick check whether we have it
			 * and the next call to findClass0
			 * can quickly resolve it too. */
			LocateableClassNode node = findClass0("java/lang/Object");
			nodeMap.put(name, node.node);
			return node;
		}

		/* try to resolve the class from the runtime. */
		try {
			ClassReader cr = new ClassReader(name);
			ClassNode cn = new ClassNode();
			cr.accept(cn, ClassReader.SKIP_CODE);
			/* cache it. */
			nodeMap.put(cn.name, cn);
			
			ClassTree tree = parent._getClassTree();
			if(tree == null) {
				if(!cn.name.equals("java/lang/Object")) {
					throw new IllegalStateException("Only Object may be loaded during tree initialisation.");
				}
			} else {
				if(!tree.containsVertex(cn)) {
					tree.addVertex(cn);
				}
			}
			
			LocateableClassNode node = new LocateableClassNode(this, cn);
			return node;
		} catch(IOException e) {
			// TODO: logger
			System.err.println(e.getMessage() + ": " + name);
			notContains.add(name);
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