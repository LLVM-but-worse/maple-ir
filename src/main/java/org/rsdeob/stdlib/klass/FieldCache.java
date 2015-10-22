package org.rsdeob.stdlib.klass;

import java.util.Collection;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.topdank.banalysis.filter.Filter;

/**
 * @author Bibl (don't ban me pls)
 * @created 8 Jul 2015 03:26:12
 */
public class FieldCache extends DataCache<FieldNode> {

	/**
	 * @param classes
	 */
	public FieldCache(Collection<ClassNode> classes) {
		super(classes);
	}

	/**
	 * @param filter
	 */
	public FieldCache(Filter<FieldNode> filter) {
		super(filter);
	}

	/**
	 * @param filter
	 * @param classes
	 */
	public FieldCache(Filter<FieldNode> filter, Collection<ClassNode> classes) {
		super(filter, classes);
	}
	
	/* (non-Javadoc)
	 * @see org.nullbool.api.obfuscation.refactor.DataCache#put(org.objectweb.asm.tree.ClassNode)
	 */
	@Override
	public void put(ClassNode cn) {
		for(FieldNode f : cn.fields) {
			put(f);
		}
	}

	/* (non-Javadoc)
	 * @see org.nullbool.api.obfuscation.refactor.DataCache#makeKey(java.lang.Object)
	 */
	@Override
	public String makeKey(FieldNode t) {
		return makeKey(t.owner.name, t.name, t.desc);
	}

	/* (non-Javadoc)
	 * @see org.nullbool.api.obfuscation.refactor.DataCache#put(java.lang.Object)
	 */
	@Override
	public void put(FieldNode f) {
		if(canCache(f)) {
			put(makeKey(f), f);
		}
	}
}