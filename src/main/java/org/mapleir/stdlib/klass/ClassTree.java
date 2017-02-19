package org.mapleir.stdlib.klass;

import static org.mapleir.stdlib.klass.ClassHelper.*;

import java.util.*;
import java.util.Map.Entry;

import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.ValueCreator;
import org.objectweb.asm.tree.ClassNode;

/**
 * @author Bibl (don't ban me pls)
 * @created 25 May 2015 (actually before this)
 */
public class ClassTree implements Iterable<ClassNode> {
	private static final ValueCreator<Set<ClassNode>> SET_CREATOR = LinkedHashSet::new;

	private final Map<String, ClassNode> classes;
	private final Map<String, ClassNode> jdkclasses;
	private final NullPermeableHashMap<ClassNode, Set<ClassNode>> supers;
	private final NullPermeableHashMap<ClassNode, Set<ClassNode>> delgates;

	public void rebuildTable() {
		Set<ClassNode> cset = new HashSet<>(classes.values());
		
		classes.clear();
		for(ClassNode cn : cset) {
			classes.put(cn.name, cn);
		}
		
		for(Entry<String, ClassNode> e : jdkclasses.entrySet()) {
			if(!e.getValue().name.equals(e.getKey())) {
				throw new IllegalStateException(e.getValue() + ", " + e.getKey());
			}
		}
	}
	
	public ClassTree(Collection<ClassNode> classes) {
		this(convertToMap(classes));
	}

	public ClassTree(Map<String, ClassNode> classes_) {
		classes  = copyOf(classes_);
		jdkclasses = new HashMap<>();
		supers   = new NullPermeableHashMap<>(SET_CREATOR);
		delgates = new NullPermeableHashMap<>(SET_CREATOR);

		build();
	}
	
	public boolean isJDKClass(ClassNode cn) {
		findClass(cn.name);
		return jdkclasses.containsKey(cn.name);
	}
	
	public ClassNode findClass(String name) {
		if(name == null) {
			return null;
		}
		if(classes.containsKey(name)) {
			return classes.get(name);
		} else if(jdkclasses.containsKey(name)) {
			return jdkclasses.get(name);
		} else {
			try {
				ClassNode cn = ClassNodeUtil.create(name);
				// create these sets
				// TODO: maybe call build?
				getSupers0(cn);
				getDelegates0(cn);
				jdkclasses.put(cn.name, cn);
				return cn;
			} catch(Exception e) {
			}
		}
		return null;
	}

	// TODO: optimise
	public void build() {
		for (ClassNode node : classes.values()) {
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
	
	public void build(ClassNode node) {
		if(!isJDKClass(node)) {
			classes.put(node.name, node);
		}
		
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
	
	/* public void output() {
		if (classes.size() == delgates.size() && classes.size() == supers.size() && delgates.size() == supers.size()) {
				System.out.println(String.format("Built tree for %d classes (%d del, %d sup).", classes.size(), delgates.size(), supers.size()));
		} else {
			System.out.println(String.format("WARNING: Built tree for %d classes (%d del, %d sup), may be erroneous.", classes.size(), delgates.size(),
					supers.size()));
		}
	} */

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
	
	public boolean containsKey(String name) {
		return classes.containsKey(name);
	}

	public ClassNode getClass(String name) {
		return classes.get(name);
	}

	private Set<ClassNode> getSupers0(ClassNode cn) {
		return supers.getNonNull(cn);
	}

	private Set<ClassNode> getDelegates0(ClassNode cn) {
		return delgates.getNonNull(cn);
	}

	public Map<String, ClassNode> getClasses() {
		return classes;
	}

	public Set<ClassNode> getSupers(ClassNode cn) {
		return Collections.unmodifiableSet(supers.get(cn));
		// return supers.get(cn);
	}

	public Set<ClassNode> getDelegates(ClassNode cn) {
		return Collections.unmodifiableSet(delgates.get(cn));
		// return delgates.get(cn);
	}

	@Override
	public Iterator<ClassNode> iterator() {
		return classes.values().iterator();
	}
	
	public Set<ClassNode> getVirtualReachableBranches(ClassNode cn, boolean exploreRuntime) {
		Set<ClassNode> set = new HashSet<>();
		
		set.addAll(getSupers(cn));
		set.addAll(getDelegates(cn));
		
		return set;
	}
	
	// gets all connected classes, both up and down
	public Set<ClassNode> getAllBranches(ClassNode cn, boolean exploreRuntime) {		
		Set<ClassNode> set = new HashSet<>();
		
		Set<ClassNode> pending = new HashSet<>();
		pending.add(cn);
		
		for(;;) {
			int size = set.size();

			Set<ClassNode> discovered = new HashSet<>();
			for(ClassNode c : pending) {
				for(ClassNode o : getSupers(c)) {
					if(isJDKClass(o) && !exploreRuntime) {
						continue;
					}
					discovered.add(o);
				}
				for(ClassNode o : getDelegates(c)) {
					if(isJDKClass(o) && !exploreRuntime) {
						continue;
					}
					discovered.add(o);
				}
			}
			
			set.addAll(pending);
			pending = discovered;
			
			if(set.size() == size) {
				break;
			}
		}
		
		return set;
	}
	
	// gets connected leaves in class tree going down.
	public Set<ClassNode> getLeafDelegates(ClassNode cn, boolean exploreRuntime) {
		Set<ClassNode> results = new HashSet<>();
		LinkedHashSet<ClassNode> todo = new LinkedHashSet<>();
		todo.add(cn);
		while (!todo.isEmpty()) {
			Iterator<ClassNode> it = todo.iterator();
			ClassNode cur = it.next();
			it.remove();
			
			Set<ClassNode> children = getDelegates(cur);
			if (children.isEmpty())
				results.add(cur);
			else for (ClassNode o : children)
				todo.add(o);
		}
		return results;
	}
}