package org.mapleir.deobimpl;

import java.util.List;

import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.deob.ICompilerPass;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class OpaquePredicateRemoverPhase2 implements ICompilerPass {
	public static final String KEY_ID = OpaquePredicateRemoverPhase.class.getCanonicalName();

	@Override
	public String getId() {
		return KEY_ID;
	}

	@Override
	public void accept(IContext cxt, ICompilerPass prev, List<ICompilerPass> completed) {
		for(ClassNode cn : cxt.getNodes().values()) {
			for(MethodNode m : cn.methods) {
				
			}
		}
	}
}