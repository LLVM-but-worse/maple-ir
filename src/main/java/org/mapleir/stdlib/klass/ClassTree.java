package org.mapleir.stdlib.klass;

import static org.mapleir.stdlib.klass.ClassHelper.*;

import java.util.*;

import org.mapleir.stdlib.collections.NullPermeableHashMap;
import org.mapleir.stdlib.collections.ValueCreator;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * @author Bibl (don't ban me pls)
 * @created 25 May 2015 (actually before this)
 */
public class ClassTree implements Iterable<ClassNode> {
	private static final ValueCreator<Set<ClassNode>> SET_CREATOR = new ValueCreator<Set<ClassNode>>() {
		@Override
		public Set<ClassNode> create() {
			return new LinkedHashSet<>();
		}
	};

	private final Map<String, ClassNode>                          classes;
	private final Map<String, ClassNode>                          jdkclasses;
	private final NullPermeableHashMap<ClassNode, Set<ClassNode>> supers;
	private final NullPermeableHashMap<ClassNode, Set<ClassNode>> delgates;

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
		return jdkclasses.containsKey(cn.name);
	}
	
	public ClassNode findClass(String name) {
		if(classes.containsKey(name)) {
			return classes.get(name);
		} else if(jdkclasses.containsKey(name)) {
			return jdkclasses.get(name);
		} else {
			try {
				ClassNode cn = ClassNodeUtil.create(name);
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
	
	public void output() {
		if (classes.size() == delgates.size() && classes.size() == supers.size() && delgates.size() == supers.size()) {
				System.out.println(String.format("Built tree for %d classes (%d del, %d sup).", classes.size(), delgates.size(), supers.size()));
		} else {
			System.out.println(String.format("WARNING: Built tree for %d classes (%d del, %d sup), may be erroneous.", classes.size(), delgates.size(),
					supers.size()));
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

	public Set<MethodNode> getMethodsFromSuper(MethodNode m) {
		return getMethodsFromSuper(m.owner, m.name, m.desc, (m.access & Opcodes.ACC_STATIC) != 0);
	}

	public Set<MethodNode> getMethodsFromSuper(ClassNode node, String name, String desc, boolean isStatic) {
		Set<MethodNode> methods = new HashSet<>();
		for (ClassNode super_ : getSupers(node)) {
			for (MethodNode mn : super_.methods) {
				if (mn.name.equals(name) && mn.desc.equals(desc) && ((mn.access & Opcodes.ACC_STATIC) != 0) == isStatic) {
					methods.add(mn);
				}
			}
		}
		return methods;
	}

	public Set<MethodNode> getMethodsFromDelegates(MethodNode m) {
		return getMethodsFromDelegates(m.owner, m.name, m.desc, (m.access & Opcodes.ACC_STATIC) != 0);
	}

	public Set<MethodNode> getMethodsFromDelegates(ClassNode node, String name, String desc, boolean isStatic) {
		Set<MethodNode> methods = new HashSet<>();
		for (ClassNode delegate : getDelegates(node)) {
			for (MethodNode mn : delegate.methods) {
				if (mn.name.equals(name) && mn.desc.equals(desc) && ((mn.access & Opcodes.ACC_STATIC) != 0) == isStatic) {
					methods.add(mn);
				}
			}
		}
		return methods;
	}

	public MethodNode getFirstMethodFromSuper(ClassNode node, String name, String desc, boolean isStatic) {
		for (ClassNode super_ : getSupers(node)) {
			for (MethodNode mn : super_.methods) {
				if (mn.name.equals(name) && mn.desc.equals(desc) && ((mn.access & Opcodes.ACC_STATIC) != 0) == isStatic) {
					return mn;
				}
			}
		}
		return null;
	}
	
	public boolean containsKey(String name) {
		return classes.containsKey(name);
	}

	public ClassNode getClass(String name) {
		return classes.get(name);
	}
	
	public MethodNode getMethodFromSuper(ClassTree tree, ClassNode cn, String name, String desc, boolean isStatic) {
		for (ClassNode super_ : tree.getSupers(cn)) {
			for (MethodNode mn : super_.methods) {
				if (mn.name.equals(name) && mn.desc.equals(desc) && ((mn.access & Opcodes.ACC_STATIC) != 0) == isStatic) {
					return mn;
				}
			}
		}
		return null;
	}
	
	public boolean isInherited(ClassTree tree, ClassNode cn, String name, String desc, boolean isStatic) {
		return getMethodFromSuper(tree, cn, name, desc, isStatic) != null;
	}

	public boolean isInherited(ClassTree tree, ClassNode owner, MethodNode mn) {
		if(owner == null) {
			throw new NullPointerException();
		}
		return mn.owner.name.equals(owner.name) && isInherited(tree, owner, mn.name, mn.desc, (mn.access & Opcodes.ACC_STATIC) != 0);
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
}