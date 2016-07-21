package org.mapleir.stdlib.klass;

import java.util.Collection;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.topdank.banalysis.filter.Filter;

/**
 * @author Bibl (don't ban me pls)
 * @created 27 Jul 2015 23:46:55
 */
public class ReverseMethodDescCache extends ReverseDataCache<MethodNode> {

	public ReverseMethodDescCache(Filter<MethodNode> filter) {
		super(filter);
	}

	public ReverseMethodDescCache(Filter<MethodNode> filter, Collection<ClassNode> classes) {
		super(filter, classes);
	}

	public ReverseMethodDescCache(Collection<ClassNode> classes) {
		super(Filter.acceptAll(), classes);
	}
	
	@Override
	public void put(ClassNode cn) {
		for(MethodNode m : cn.methods) {
			put(m);
		}
	}

	@Override
	public String makeVal(MethodNode t) {
		return t.desc;
	}

	@Override
	public void put(MethodNode m) {
		if(canCache(m)) {
			put(m, makeVal(m));
		}
	}
}