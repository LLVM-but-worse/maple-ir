package org.mapleir.stdlib.klass;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class InvocationResolver {

	private final ClassTree tree;
	
	public InvocationResolver(ClassTree tree) {
		this.tree = tree;
	}
	
	public MethodNode resolveVirtualInitCall(String owner, String desc) {
		Set<MethodNode> set = new HashSet<>();
		
		ClassNode cn = tree.getClass(owner);
		
		if(cn != null) {
			for(MethodNode m : cn.methods) {
				if((m.access & Opcodes.ACC_STATIC) == 0) {
					if(m.name.equals("<init>") && m.desc.equals(desc)) {
						set.add(m);
					}
				}
			}
			
			if(set.size() == 1) {
				return set.iterator().next();
			} else {
				throw new IllegalStateException(String.format("Looking for: %s.<init>%s, got: %s", owner, desc, set));
			}
		} else {
			return null;
		}
	}
	
	public MethodNode resolveVirtualCall(ClassNode cn, String name, String desc) {
		Set<MethodNode> set = new HashSet<>();
		for(MethodNode m : cn.methods) {
			if((m.access & Opcodes.ACC_STATIC) == 0) {
				if(m.name.equals(name) && m.desc.equals(desc)) {
					set.add(m);
				}
			}
		}
		
		if(set.size() > 1) {
			throw new IllegalStateException(cn.name + "." + name + " " + desc + " => " + set);
		}
		
		if(set.size() == 1) {
			return set.iterator().next();
		} else {
			return null;
		}
	}
	
	private Set<MethodNode> getVirtualMethods(Set<ClassNode> classes, String name, String desc) {
		Set<MethodNode> set = new HashSet<>();
		for(ClassNode cn : classes) {
			for(MethodNode m : cn.methods) {
				if(!Modifier.isStatic(m.access)) {
					if(m.name.equals(name) && m.desc.equals(desc)) {
						set.add(m);
					}
				}
			}
		}
		return set;
	}
	
	public Set<MethodNode> resolveVirtualCalls(String owner, String name, String desc) {
		Set<MethodNode> set = new HashSet<>();
		
		ClassNode cn = tree.getClass(owner);
		
		if(cn != null) {
//			Set<ClassNode> classes = tree.getAllBranches(cn, true);
//			set.addAll(getVirtualMethods(classes, name, desc));
			MethodNode m = resolveVirtualCall(cn, name, desc);
			if(m != null) {
				set.add(m);
			}
			
			for(ClassNode subC : tree.getSupers(cn)) {
				m = resolveVirtualCall(subC, name, desc);
				if(m != null) {
					set.add(m);
				}
			}
			
			for(ClassNode subC : tree.getDelegates(cn)) {
				m = resolveVirtualCall(subC, name, desc);
				if(m != null) {
					set.add(m);
				}
			}
			
			return set;
			// throw new IllegalStateException(cn.name + "." + name + " " + desc);
		}
		
		return set;
	}
	
	public MethodNode resolveStaticCall(String owner, String name, String desc) {
		Set<MethodNode> set = new HashSet<>();
		
		ClassNode cn = tree.getClass(owner);
		
		if(cn != null) {
			for(MethodNode m : cn.methods) {
				if((m.access & Opcodes.ACC_STATIC) != 0) {
					if(m.name.equals(name) && m.desc.equals(desc)) {
						set.add(m);
					}
				}
			}
			
			if(set.size() == 0) {
				return resolveStaticCall(cn.superName, name, desc);
			} else if(set.size() == 1) {
				return set.iterator().next();
			} else {
				throw new IllegalStateException(owner + "." + name + " " + desc + ",   " + set.toString());
			}
		} else {
			return null;
		}
	}
}