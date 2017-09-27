package org.topdank.byteengineer.commons.classloader;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.tree.ClassNode;
import org.topdank.byteengineer.commons.asm.ASMFactory;
import org.topdank.byteengineer.commons.asm.DefaultASMFactory;
import org.topdank.byteengineer.commons.data.LocateableJarContents;

import sun.misc.URLClassPath;

/**
 * Specific {@link ClassLoader} for loading things from external JarFiles, caching loaded classes as it goes along. <br>
 *
 * @author Bibl
 */
public class JarClassLoader extends ClassLoader {

	private final ClassLoader myParent;
	private ASMFactory<ClassNode> factory;

	private Map<String, ClassNode> fastNodeCache;
	private Map<String, Class<?>> cache;
	private URLClassPath ucp;

	public JarClassLoader(LocateableJarContents<ClassNode> contents) {
		this(contents, null, new DefaultASMFactory());
	}

	/**
	 * @param contents Reference to the JarContents object.
	 */
	public JarClassLoader(LocateableJarContents<ClassNode> contents, ClassLoader parent) {
		this(contents, parent, new DefaultASMFactory());
	}

	/**
	 * @param contents Reference to the JarContents object.
	 * @param parent
	 * @param factory ASMFactory.
	 */
	public JarClassLoader(LocateableJarContents<ClassNode> contents, ClassLoader parent, ASMFactory<ClassNode> factory) {
		this.factory = factory;

		ClassLoader _parent = parent;
		if (_parent == null)
			_parent = getClass().getClassLoader();
		myParent = _parent;

		cache = new HashMap<>();
		fastNodeCache = new HashMap<>();
		ucp = new URLClassPath(new URL[0]);
		add(contents);

		StackTraceElement e = creator(false);
		System.err.println(
				String.format("Creating new JarClassLoader, parent ClassLoader: %s of %s.%s:%d", myParent, e.getClassName(), e.getMethodName(),
						e.getLineNumber()));
	}

	private static StackTraceElement creator(boolean cl) {
		StackTraceElement[] el = new Exception().getStackTrace();
		String thisName = JarClassLoader.class.getCanonicalName();
		if (cl)
			thisName = "ClassLoader";
		for (int i = 0; i < el.length; i++) {
			StackTraceElement e = el[i];
			String klass = e.getClassName();
			if (!klass.contains(thisName))
				return e;
		}
		return null;
	}

	public void add(LocateableJarContents<ClassNode> contents) {
		for (URL url : contents.getJarUrls()) {
			ucp.addURL(url);
		}
		for (ClassNode cn : contents.getClassContents()) {
			fastNodeCache.put(cn.name, cn);
		}
	}

	@Override
	public URL findResource(String name) {
		return ucp.findResource(name, true);
	}

	/**
	 * Defines and caches a ClassNode.
	 *
	 * @param cn ClassNode to define
	 * @return Defined Class.
	 */
	public Class<?> defineNode(ClassNode cn) {
		byte[] bytes = factory.write(cn);
		Class<?> c = defineClass(cn.name.replace("/", "."), bytes, 0, bytes.length);
		cache(c, cn.name);
		fastNodeCache.put(cn.name, cn);
		return c;
	}

	public void forceCache(Class<?> c, String name) {
		cache.put(name.replace(".", "/"), c);
	}

	public void cache(Class<?> c, String name) {
		if ((c != null) && !name.startsWith("java")) {
			cache.put(name.replace(".", "/"), c);
		}
	}

	@Override
	public Class<?> findClass(String name) throws ClassNotFoundException {
		name = name.replace(".", "/");
		// try the loaded cache
		if (cache.containsKey(name))
			return cache.get(name);

		// try the node cache
		ClassNode node = fastNodeCache.get(name);
		if (node != null)
			return defineNode(node);

		// Logger.getDefaultLogger().debug(this.getClass().getSimpleName() + " from: " + creator(true));

		Class<?> c = myParent.loadClass(name);
		cache(c, name);
		return c;
	}
}