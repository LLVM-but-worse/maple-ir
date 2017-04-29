package org.mapleir.context;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import org.mapleir.context.app.ApplicationClassSource;
import org.mapleir.context.app.ClassTree;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class SimpleApplicationContext extends AbstractApplicationContext {

	private final ApplicationClassSource app;
	
	public SimpleApplicationContext(ApplicationClassSource app) {
		this.app = app;
	}

	private boolean isMainMethod(MethodNode m) {
		return Modifier.isPublic(m.access) && Modifier.isStatic(m.access) && m.name.equals("main") && m.desc.equals("([Ljava/lang/String;)V");
	}
	
	private boolean isLibraryInheritedMethod(MethodNode m, ClassTree tree) {
		return false;
	}
	
	@Override
	protected Set<MethodNode> computeEntryPoints() {
		ClassTree tree = app.getClassTree();
		
		Set<MethodNode> set = new HashSet<>();
		
		for(ClassNode cn : app.iterate()) {
			for(MethodNode m : cn.methods) {
				if(isMainMethod(m)) {
					set.add(m);
				} else if(!Modifier.isStatic(m.access) && isLibraryInheritedMethod(m, tree)) {
					set.add(m);
				}
			}
		}
		
		return set;
	}
}