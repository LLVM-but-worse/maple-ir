package org.mapleir.state;

import org.mapleir.state.structures.ClassResolver;
import org.mapleir.state.structures.ClassTree;
import org.mapleir.stdlib.collections.IteratorIterator;
import org.mapleir.stdlib.klass.ClassHelper;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public class ApplicationClassSource extends ClassSource {

	private final String name;
	private final Collection<LibraryClassSource> libraries;
	private ClassResolver resolver;
	private ClassTree classTree;
	
	public ApplicationClassSource(String name, Collection<ClassNode> classes) {
		this(name, ClassHelper.convertToMap(classes));
	}
	
	public ApplicationClassSource(String name, Map<String, ClassNode> nodeMap) {
		super(nodeMap);
		this.name = (name == null ? "unknown" : name);
		libraries = new ArrayList<>();
	}
	
	public ClassResolver getResolver() {
		if(resolver == null) {
			resolver = new ClassResolver(this);
		}
		return resolver;
	}
	
	public ClassTree getStructures() {
		if (classTree == null) {
			classTree = new ClassTree(this);
			for (ClassNode node : iterateWithLibraries()) {
				classTree.addVertex(node);
			}
		}
		return classTree;
	}
	
	@Override
	public void rebuildTable() {
		// rebuild app table
		super.rebuildTable();
		// rebuild lib tables
		for(LibraryClassSource lib : libraries) {
			lib.rebuildTable();
		}
	}
	
	public void addLibraries(LibraryClassSource... libs) {
		for(LibraryClassSource cs : libs) {
			if(!libraries.contains(cs)) {
				libraries.add(cs);
			}
		}
		resolver = new ClassResolver(this);
	}
	
	public ClassNode findClassNode(String name) {
		LocateableClassNode n = findClass(name);
		
		if(n != null) {
			ClassNode cn = n.node;
			return cn;
		} else {
			return null;
		}
	}
	
	public LocateableClassNode findClass(String name) {
		if(name == null) {
			return null;
		}
		
		LocateableClassNode node = super.findClass0(name);
		
		if(node != null) {
			return node;
		} else {
			for(LibraryClassSource cs : libraries) {
				if(cs.contains(name)) {
					return cs.findClass0(name);
				}
			}
			return null;
		}
	}

	public boolean isLibraryClass(String name) {
		if(name == null) {
			return false;
		}
		
		/* quick check to see if it's app class instead. 
		 * (prevents attempted loading by runtime lib). */
		if(contains(name)) {
			return false;
		}
		
		for(LibraryClassSource cs : libraries) {
			if(cs.contains(name)) {
				return true;
			}
		}
		return false;
	}

	public boolean isApplicationClass(String name) {
		if(name == null) {
			return false;
		}
		
		return contains(name);
	}
	
	public Iterable<ClassNode> iterateWithLibraries() {
		return new Iterable<ClassNode>() {
			Iterator<LibraryClassSource> libIt = null;
			@Override
			public Iterator<ClassNode> iterator() {
				return new IteratorIterator<ClassNode>() {
					@Override
					public Iterator<ClassNode> nextIterator() {
						if(libIt == null) {
							libIt = libraries.iterator();
							return ApplicationClassSource.this.iterator();
						} else {
							if(libIt.hasNext()) {
								LibraryClassSource lib = libIt.next();
								if(lib.isIterable()) {
									return lib.iterator();
								} else {
									return nextIterator();
								}
							} else {
								return null;
							}
						}
					}
				};
			}
		};
	}
	
	@Override
	public String toString() {
		return name;
	}
}