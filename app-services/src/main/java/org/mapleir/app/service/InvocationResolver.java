package org.mapleir.app.service;

import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public interface InvocationResolver {

	MethodNode resolveStaticCall(String owner, String name, String desc);
	
	MethodNode resolveVirtualInitCall(String owner, String desc);
	
	Set<MethodNode> resolveVirtualCalls(String owner, String name, String desc, boolean strict);
	
	default Set<MethodNode> resolveVirtualCalls(MethodNode m, boolean strict) {
		return resolveVirtualCalls(m.owner.name, m.name, m.desc, strict);
	}
	
	FieldNode findStaticField(String owner, String name, String desc);
	
	FieldNode findVirtualField(String owner, String name, String desc);
	
	default FieldNode findField(String owner, String name, String desc, boolean isStatic) {
		if(isStatic) {
			return findStaticField(owner, name, desc);
		} else {
			return findVirtualField(owner, name, desc);
		}
	}
	
	Set<MethodNode> getHierarchyMethodChain(ClassNode cn, String name, String desc, boolean exact);
}