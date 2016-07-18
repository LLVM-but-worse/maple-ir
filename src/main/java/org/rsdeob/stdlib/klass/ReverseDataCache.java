package org.rsdeob.stdlib.klass;

import org.objectweb.asm.tree.ClassNode;
import org.topdank.banalysis.filter.Filter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Bibl (don't ban me pls)
 * @created 27 Jul 2015 23:44:24
 */
public abstract class ReverseDataCache<T> {

	private final Filter<T> filter;
	private final Map<T, String> map;

	public ReverseDataCache(Filter<T> filter) {
		this.filter = filter;
		map = new HashMap<T, String>();
	}

	public ReverseDataCache(Filter<T> filter, Collection<ClassNode> classes) {
		this(filter);
		put(classes);
	}

	public ReverseDataCache(Collection<ClassNode> classes) {
		this(Filter.acceptAll(), classes);
	}

	public void put(Collection<ClassNode> classes) {
		for (ClassNode cn : classes) {
			put(cn);
		}
	}

	public boolean canCache(T t) {
		return filter.accept(t);
	}

	public abstract void put(ClassNode cn);

	public abstract String makeVal(T t);

	public abstract void put(T t);

	public void put(T t, String key) {
		map.put(t, key);
	}
	
	public void reset() {
		Collection<T> methods = map.keySet();
		map.clear();
		for (T m : methods) {
			put(m);
		}
	}

	public static String makeKey(String owner, String name, String desc) {
		return new StringBuilder(owner).append(".").append(name).append(desc).toString();
	}

	public String get(T t) {
		if (map.containsKey(t))
			return map.get(t);
		return null;
	}

	public void clear() {
		map.clear();
	}

	public int size() {
		return map.size();
	}
}