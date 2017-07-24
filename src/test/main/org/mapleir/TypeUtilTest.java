package org.mapleir;

import static org.junit.Assert.*;
import static org.mapleir.stdlib.util.TypeUtils.*;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.mapleir.context.app.ApplicationClassSource;
import org.mapleir.context.app.InstalledRuntimeClassSource;
import org.mapleir.stdlib.collections.ClassHelper;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

public class TypeUtilTest {

	class X {}
	class Y extends X {}
	class Z extends X {}
	abstract class W extends Y implements Iterable<Object>{}
	
	@Test
	public void test() {
		Map<Class<?>, ClassNode> nodes = new HashMap<>();
		for(Class<?> c : new Class<?>[] {X.class, Y.class, Z.class, W.class}) {
			nodes.put(c, ClassHelper.create(c.getName().replace(".", "/")));
		}
		
		ApplicationClassSource source = new ApplicationClassSource("testSource", nodes.values());
		source.addLibraries(new InstalledRuntimeClassSource(source));
		
		assertTrue(castNeverFails(source, OBJECT_TYPE, OBJECT_TYPE));
		assertFalse(castNeverFails(source, OBJECT_TYPE, SERIALIZABLE_TYPE));

		// self
		assertTrue(castNeverFails(source, t(X.class), t(X.class)));
		assertTrue(castNeverFails(source, t(Y.class), t(Y.class)));
		assertTrue(castNeverFails(source, t(Z.class), t(Z.class)));
		assertTrue(castNeverFails(source, t(W.class), t(W.class)));

		// super
		assertTrue(castNeverFails(source, t(Y.class), t(X.class)));
		assertTrue(castNeverFails(source, t(Z.class), t(X.class)));
		assertTrue(castNeverFails(source, t(W.class), t(X.class)));
		assertTrue(castNeverFails(source, t(W.class), t(Y.class)));
		assertTrue(castNeverFails(source, t(W.class), t(Iterable.class)));

		assertFalse(castNeverFails(source, t(W.class), t(Z.class)));
		assertFalse(castNeverFails(source, t(Z.class), t(W.class)));
		
		// inverse
		assertFalse(castNeverFails(source, t(X.class), t(Y.class)));
		assertFalse(castNeverFails(source, t(W.class), t(Z.class)));
		assertFalse(castNeverFails(source, t(X.class), t(W.class)));
		assertFalse(castNeverFails(source, t(Y.class), t(W.class)));
		assertFalse(castNeverFails(source, t(Iterable.class), t(W.class)));
		
		assertFalse(castNeverFails(source, t(Iterable.class), t(W.class)));
		assertTrue(castNeverFails(source, t(W.class), t(Iterable.class)));

		// not true
		assertFalse(castNeverFails(source, t(X.class), t(Iterable.class)));
		assertFalse(castNeverFails(source, t(Y.class), t(Iterable.class)));
		assertFalse(castNeverFails(source, t(Z.class), t(Iterable.class)));
		
		// arrays
		assertTrue(castNeverFails(source, t(int[].class), SERIALIZABLE_TYPE));
		assertTrue(castNeverFails(source, t(int[].class), CLONEABLE_TYPE));
		assertTrue(castNeverFails(source, t(int[].class), OBJECT_TYPE));

		// Serializable[] sz = new int[0][0];
		// Serializable kkk = sz;
		assertTrue(castNeverFails(source, t(int[][].class), t(Serializable[].class)));
		assertTrue(castNeverFails(source, t(int[][].class), t(Serializable.class)));
		
	}
	
	private static Type t(Class<?> c) {
		return Type.getType(c);
	}
}