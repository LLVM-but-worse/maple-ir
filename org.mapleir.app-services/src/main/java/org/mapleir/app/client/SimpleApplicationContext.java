package org.mapleir.app.client;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.app.service.ClassTree;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class SimpleApplicationContext extends AbstractApplicationContext {

	private final ApplicationClassSource app;
	
	public SimpleApplicationContext(ApplicationClassSource app) {
		this.app = app;
	}

	public static boolean isMainMethod(MethodNode m) {
		return Modifier.isPublic(m.access) && Modifier.isStatic(m.access) && m.name.equals("main") && m.desc.equals("([Ljava/lang/String;)V");
	}
	
	private boolean isLibraryInheritedMethod(MethodNode m) {
		if(Modifier.isStatic(m.access) || m.name.equals("<init>")) {
			return false;
		}
		
		ClassTree tree = app.getClassTree();
		
		// TODO: could probably optimise with dfs instead of getAll
		Collection<ClassNode> parents = tree.getAllParents(m.owner);
		for(ClassNode cn : parents) {
			if(app.isLibraryClass(cn.name)) {
				for(MethodNode cnM : cn.methods) {
					if(!Modifier.isStatic(cnM.access) && cnM.name.equals(m.name) && cnM.desc.equals(m.desc)) {
						return true;
					}
				}
			}
		}
		return false;
	}
	
	@Override
	protected Set<MethodNode> computeEntryPoints() {
		Set<MethodNode> set = new HashSet<>();
		
		for(ClassNode cn : app.iterate()) {
			for(MethodNode m : cn.methods) {
				if(isMainMethod(m) || m.name.equals("<clinit>") || isLibraryInheritedMethod(m)) {
					set.add(m);
				}
			}
		}
		
		return set;
	}
}
