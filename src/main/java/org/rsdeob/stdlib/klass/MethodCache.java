package org.rsdeob.stdlib.klass;

import java.util.Collection;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.topdank.banalysis.filter.Filter;

/**
 * @author Bibl (don't ban me pls)
 * @created 8 Jul 2015 03:23:35
 */
public class MethodCache extends DataCache<MethodNode> {

	public MethodCache(Collection<ClassNode> classes) {
		super(classes);
	}

	public MethodCache(Filter<MethodNode> filter) {
		super(filter);
	}

	public MethodCache(Filter<MethodNode> filter, Collection<ClassNode> classes) {
		super(filter, classes);
	}

	@Override
	public void put(ClassNode cn) {
		for(MethodNode m : cn.methods) {
			put(m);
		}
	}

	@Override
	public String makeKey(MethodNode t) {
		return makeKey(t.owner.name, t.name, t.desc);
	}
	
	@Override
	public void put(MethodNode m) {
		if(canCache(m)) {
			put(makeKey(m), m);
			m.cacheKey();
		}
	}
}