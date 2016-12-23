package org.mapleir.deobimpl;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.mapleir.stdlib.IContext;
import org.mapleir.stdlib.deob.ICompilerPass;
import org.mapleir.stdlib.klass.ClassTree;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public class UnusedFieldsPhase implements ICompilerPass, Opcodes {
	
	public static final String KEY_ID = UnusedFieldsPhase.class.getCanonicalName();

	@Override
	public String getId() {
		return KEY_ID;
	}

	@Override
	public void accept(IContext cxt, ICompilerPass prev, List<ICompilerPass> completed) {
		ClassTree tree = new ClassTree(cxt.getNodes());
		Set<FieldNode> traced = new HashSet<FieldNode>();
		int untraceable = 0, removed = 0;
		
		for(ClassNode cn : tree.getClasses().values()) {
			for(MethodNode m : cn.methods) {
				for(AbstractInsnNode ain : m.instructions.toArray()) {
					if(ain.opcode() == GETSTATIC || ain.opcode() == PUTSTATIC) {
						FieldInsnNode fin = (FieldInsnNode) ain;
						FieldNode ref = findReference(tree, fin.owner, fin.halfKey(), true);
						if(ref == null) {
							untraceable++;
						} else {
							traced.add(ref);
						}
					}
				}
			}
		}

		System.out.printf("   Traced %d static field calls.%n", traced.size());
		System.out.printf("   Couldn't trace %d static field calls.%n", untraceable);
		
		for(ClassNode cn : tree) {
			Iterator<FieldNode> it = cn.fields.iterator();
			while(it.hasNext()) {
				FieldNode f = it.next();
				if(Modifier.isStatic(f.access)) {
					if(!traced.contains(f)) {
						it.remove();
						removed++;
					}
				}
			}
		}
		
		System.out.printf("   Removed %d compiler inlined constants.%n", removed);
	}
	
	private static FieldNode findReference(ClassTree tree, String owner, String halfKey, boolean isStatic) {
		ClassNode startNode = tree.getClass(owner);
		if(startNode == null)
			return null;
		
		FieldNode field = findReference(startNode, halfKey, isStatic);
		if(field != null) 
			return field;

		Set<ClassNode> supers = tree.getSupers(startNode);
		for(ClassNode cn : supers) {
			field = findReference(cn, halfKey, isStatic);
			if(field != null)
				return field;
		}
		
		//isn't called
		return null;
	}
	
	private static FieldNode findReference(ClassNode cn, String halfKey, boolean isStatic) {
		for(FieldNode f : cn.fields) {
			if(f.halfKey().equals(halfKey) && Modifier.isStatic(f.access) == isStatic)
				return f;
		}
		return null;
	}
}