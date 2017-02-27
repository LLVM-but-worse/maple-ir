package org.mapleir.stdlib.klass.library;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.ValueCreator;
import org.objectweb.asm.tree.ClassNode;

public class ClassStructures {
	private static final ValueCreator<Set<ClassNode>> SET_CREATOR = LinkedHashSet::new;

	private final ApplicationClassSource source;
	private final NullPermeableHashMap<ClassNode, Set<ClassNode>> supers;
	private final NullPermeableHashMap<ClassNode, Set<ClassNode>> delgates;
	
	public ClassStructures(ApplicationClassSource source) {
		this.source = source;
		supers   = new NullPermeableHashMap<>(SET_CREATOR);
		delgates = new NullPermeableHashMap<>(SET_CREATOR);

		build();
	}
	
	private ClassNode findClass(String name) {
		LocateableClassNode n = source.findClass(name);
		if(n != null) {
			ClassNode cn = n.node;
			getSupers0(cn);
			getDelegates0(cn);
			return cn;
		} else {
			return null;
		}
	}
	
	private void build() {
		for (ClassNode node : source.iterateWithLibraries()) {
			for (String iface : node.interfaces) {
				ClassNode ifacecs = findClass(iface);
				if (ifacecs == null)
					continue;

				getDelegates0(ifacecs).add(node);

				Set<ClassNode> superinterfaces = new HashSet<>();
				buildSubTree(superinterfaces, ifacecs);

				getSupers0(node).addAll(superinterfaces);
			}
			ClassNode currentSuper = findClass(node.superName);
			while (currentSuper != null) {
				getDelegates0(currentSuper).add(node);
				getSupers0(node).add(currentSuper);
				for (String iface : currentSuper.interfaces) {
					ClassNode ifacecs = findClass(iface);
					if (ifacecs == null)
						continue;
					getDelegates0(ifacecs).add(currentSuper);
					Set<ClassNode> superinterfaces = new HashSet<>();
					buildSubTree(superinterfaces, ifacecs);
					getSupers0(currentSuper).addAll(superinterfaces);
					getSupers0(node).addAll(superinterfaces);
				}
				currentSuper = findClass(currentSuper.superName);
			}

			getSupers0(node);
			getDelegates0(node);
		}
	}
	
	private void buildSubTree(Collection<ClassNode> superinterfaces, ClassNode current) {
		superinterfaces.add(current);
		for (String iface : current.interfaces) {
			ClassNode cs = findClass(iface);
			if(cs != null) {
				getDelegates0(cs).add(current);
				buildSubTree(superinterfaces, cs);
			} else {
				//System.out.println("Null interface -> " + iface);
			}
		}
	}
	
	public Set<ClassNode> getSupers0(ClassNode cn) {
		return supers.getNonNull(cn);
	}

	public Set<ClassNode> getDelegates0(ClassNode cn) {
		return delgates.getNonNull(cn);
	}
	
	public Set<ClassNode> getSupers(ClassNode cn) {
		return Collections.unmodifiableSet(supers.get(cn));
	}

	public Set<ClassNode> getDelegates(ClassNode cn) {
		return Collections.unmodifiableSet(delgates.get(cn));
	}
	
	// gets all connected classes, both up and down
	public Set<ClassNode> getAllBranches(ClassNode cn, boolean exploreLibs) {
		Set<ClassNode> set = new HashSet<>();

		Set<ClassNode> pending = new HashSet<>();
		pending.add(cn);

		for (;;) {
			int size = set.size();

			Set<ClassNode> discovered = new HashSet<>();
			for (ClassNode c : pending) {
				for (ClassNode o : getSupers(c)) {
					if(source.isLibraryClass(o.name) && !exploreLibs) {
						continue;
					}
					if(o.name.equals("java/lang/Object")) {
						discovered.add(o);
					}
				}
				for (ClassNode o : getDelegates(c)) {
					if(source.isLibraryClass(o.name) && !exploreLibs) {
						continue;
					}
					discovered.add(o);
				}
			}

			set.addAll(pending);
			pending = discovered;

			if (set.size() == size) {
				break;
			}
		}

		return set;
	}

	public Set<ClassNode> getVirtualReachableBranches(ClassNode cn) {
		Set<ClassNode> set = new HashSet<>();
		
		set.addAll(getSupers(cn));
		set.addAll(getDelegates(cn));
		
		return set;
	}
}