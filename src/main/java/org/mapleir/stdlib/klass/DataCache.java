package org.mapleir.stdlib.klass;

import org.objectweb.asm.tree.ClassNode;
import org.topdank.banalysis.filter.Filter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Bibl (don't ban me pls)
 * @created 25 May 2015 (actually before this)
 */
public abstract class DataCache<T> {
	private final Filter<T> filter;
	private final Map<String, T> map;

	public DataCache(Filter<T> filter) {
		this.filter = filter;
		map = new HashMap<String, T>();
	}

	public DataCache(Filter<T> filter, Collection<ClassNode> classes) {
		this(filter);
		put(classes);
	}

	public DataCache(Collection<ClassNode> classes) {
		this(Filter.acceptAll(), classes);
	}

	// TODO:
	// public void apply(ClassTree tree) {
	// for(Entry<String, MethodNode> e: methodMap.entrySet()) {
	// MethodNode m = e.getValue();
	// String key = e.getKey();
	// ClassNode owner = m.owner;
	// Set<ClassNode> sups = tree.getSupers(owner);
	// Set<ClassNode> dels = tree.getDelegates(owner);
	//
	// for(ClassNode sup : sups) {
	//
	// }
	// }
	// }

	// you have no idea how much I wished the compiler inlined these...

	public void put(Collection<ClassNode> classes) {
		for (ClassNode cn : classes) {
			put(cn);
		}
	}

	public boolean canCache(T t) {
		return filter.accept(t);
	}

	public abstract void put(ClassNode cn);

	public abstract String makeKey(T t);

	public abstract void put(T t);

	public void put(String key, T t) {
		map.put(key, t);
	}
	
	public void reset() {
		Collection<T> methods = map.values();
		map.clear();
		for (T m : methods) {
			put(m);
		}
	}

	public static String makeKey(String owner, String name, String desc) {
		return new StringBuilder(owner).append(".").append(name).append(desc).toString();
	}

	public T get(String owner, String name, String desc) {
		return get(makeKey(owner, name, desc));
	}

	public T get(String key) {
		if (map.containsKey(key))
			return map.get(key);
		return null;
	}

	public void clear() {
		map.clear();
	}

	public int size() {
		return map.size();
	}
}