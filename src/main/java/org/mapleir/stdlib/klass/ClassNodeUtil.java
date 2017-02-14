package org.mapleir.stdlib.klass;

import java.io.IOException;
import java.io.InputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

public class ClassNodeUtil {

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
//			System.err.println(e.getMessage() + ": " + name + "  (" + Integer.toBinaryString(flags) + ")");
			return null;
		}
	}
}