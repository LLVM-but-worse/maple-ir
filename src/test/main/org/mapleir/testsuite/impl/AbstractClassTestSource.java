package org.mapleir.testsuite.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.mapleir.testsuite.api.Logger;
import org.mapleir.testsuite.api.TestSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

public abstract class AbstractClassTestSource implements TestSource {
	
	public abstract Iterator<InputStream> openClassStreams();
	
	@Override
	public Set<ClassNode> loadTestClasses() throws IOException {
		Set<ClassNode> classes = new HashSet<>();
		
		Iterator<InputStream> iterator = openClassStreams();
		while(iterator.hasNext()) {
			try {
				InputStream is = iterator.next();
				
				if(is != null) {
					loadClass(classes, is);
					
					Logger.debug(String.format("Closing stream: %s (%d).%n", is, System.identityHashCode(is)));
					is.close();
				} else {
					Logger.warn("Received null stream.");
				}
			} catch(IOException e) {
				Logger.error(e);
			}
		}
		
		return classes;
	}
	
	protected void loadClass(Set<ClassNode> classes, InputStream is) throws IOException {
		ClassReader cr = new ClassReader(is);
		ClassNode cn = new ClassNode();
		cr.accept(cn, 0);
		
		classes.add(cn);
	}
}