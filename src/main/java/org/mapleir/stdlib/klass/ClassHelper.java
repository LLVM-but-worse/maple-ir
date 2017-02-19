package org.mapleir.stdlib.klass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.objectweb.asm.tree.ClassNode;

/**
 * @author Bibl (don't ban me pls)
 * @created 25 May 2015 (actually before this)
 */
public class ClassHelper {

	public static Map<String, ClassNode> convertToMap(Collection<ClassNode> classes) {
		Map<String, ClassNode> map = new HashMap<>();
		for (ClassNode cn : classes) {
			map.put(cn.name, cn);
		}
		return map;
	}
	
	public static <T, K> Map<T, K> copyOf(Map<T, K> src) {
		Map<T, K> dst = new HashMap<>();
		copy(src, dst);
		return dst;
	}
	
	public static <T, K> void copy(Map<T, K> src, Map<T, K> dst) {
		for(Entry<T, K> e : src.entrySet()) {
			dst.put(e.getKey(), e.getValue());
		}
	}
	
	public static <T> List<T> collate(Iterator<T> it){
		List<T> list = new ArrayList<>();
		while(it.hasNext()) {
			list.add(it.next());
		}
		return list;
	}
}