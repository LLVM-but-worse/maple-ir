package org.mapleir.stdlib.klass;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.objectweb.asm.ClassReader;
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
	
	public static ClassNode create(byte[] bytes) {
		return create(bytes, ClassReader.SKIP_FRAMES);
	}

	public static ClassNode create(byte[] bytes, int flags) {
		ClassReader reader = new ClassReader(bytes);
		ClassNode node = new ClassNode();
		reader.accept(node, flags);
		return node;
	}

	public static ClassNode create(InputStream in) {
		return create(in, ClassReader.SKIP_FRAMES);
	}

	public static ClassNode create(InputStream in, int flags) {
		try {
			ClassReader cr = new ClassReader(in);
			ClassNode cs = new ClassNode();
			cr.accept(cs, flags);
			return cs;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static ClassNode create(String name) {
		return create(name, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
	}

	public static ClassNode create(String name, int flags) {
		try {
			ClassReader cr = new ClassReader(name.replace(".", "/"));
			ClassNode cs = new ClassNode();
			cr.accept(cs, flags);
			return cs;
		} catch (IOException e) {
			// TODO: log print
			return null;
		}
	}
}