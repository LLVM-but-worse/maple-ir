package org.mapleir.stdlib.collections;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/**
 * @author Bibl (don't ban me pls)
 */
public class ClassHelper {

	public static Collection<ClassNode> parseClasses(Class<?>... a) throws IOException {
		List<ClassNode> list = new ArrayList<>();
		for(int i=0; i < a.length; i++) {
			ClassNode cn = new ClassNode();
			ClassReader cr = new ClassReader(a[i].getName());
			cr.accept(cn, 0);
			list.add(cn);
		}
		return list;
	}
	
	public static Map<String, ClassNode> convertToMap(Collection<ClassNode> classes) {
		Map<String, ClassNode> map = new HashMap<>();
		for (ClassNode cn : classes) {
			map.put(cn.name, cn);
		}
		return map;
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
