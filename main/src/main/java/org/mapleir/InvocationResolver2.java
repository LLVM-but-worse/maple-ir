package org.mapleir;

import java.util.Set;

import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.InvocationResolver;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class InvocationResolver2 implements InvocationResolver {
	private final ApplicationClassSource app;

	public InvocationResolver2(ApplicationClassSource app) {
		this.app = app;
	}

	@Override
	public MethodNode resolveStaticCall(String owner, String name, String desc) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MethodNode resolveVirtualInitCall(String owner, String desc) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<MethodNode> resolveVirtualCalls(String owner, String name, String desc, boolean strict) {
		ClassNode cn = app.findClassNode(name);
		
		
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public FieldNode findStaticField(String owner, String name, String desc) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public FieldNode findVirtualField(String owner, String name, String desc) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Set<MethodNode> getHierarchyMethodChain(ClassNode cn, String name, String desc, boolean exact) {
		// TODO Auto-generated method stub
		return null;
	}
}
