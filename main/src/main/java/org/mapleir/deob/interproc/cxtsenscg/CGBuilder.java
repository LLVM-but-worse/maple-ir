package org.mapleir.deob.interproc.cxtsenscg;

import java.util.HashSet;
import java.util.Set;

import org.mapleir.app.service.ApplicationClassSource;
import org.mapleir.deob.interproc.geompa.PointsToAnalysis;
import org.mapleir.deob.interproc.geompa.util.QueueReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class CGBuilder {
	
	private final PointsToAnalysis pa;
	private final ReachabilityMatrix rm;
	private final OFCGB ofcgb;
	private final ContextInsensitiveCallGraph cg;
	
	public CGBuilder(ApplicationClassSource app, PointsToAnalysis pa) {
		this.pa = pa;
		cg = new ContextInsensitiveCallGraph();
		rm = new ReachabilityMatrix(cg, getEntryPoints(app).iterator());
		ofcgb = new OFCGB(rm);
	}
	
	public void build() {
		QueueReader<MethodNode> worklist = rm.listener();
		while(true) {
			ofcgb.processReachables();
			rm.update();
			
			if(!worklist.hasNext()) {
				break;
			}
			
			MethodNode next = worklist.next();
			
		}
	}
	
	private static Set<MethodNode> getEntryPoints(ApplicationClassSource app) {		
		Set<MethodNode> set = new HashSet<>();
		
		// explicit entry points
		for(ClassNode cn : app.iterate()) {
			for(MethodNode m : cn.methods) {
				// public static void main(String[] _){}
				if(((Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC) & m.access) != 0 && m.name.equals("main") && m.desc.equals("([Ljava/lang/String;)V")) {
					addSafe(set, m);
				}
			}
		}
		
		addSafe(set, find(app, "java/lang/System", "initializeSystemClass", "()V"));
		addSafe(set, find(app, "java/lang/ThreadGroup", "<init>", "()V"));
		addSafe(set, find(app, "java/lang/ThreadGroup", "uncaughtException", "(Ljava/lang/Thread;Ljava/lang/Throwable;)V"));
		addSafe(set, find(app, "java/lang/Thread", "exit", "()V"));
		addSafe(set, find(app, "java/lang/ClassLoader", "<init>", "()V"));
		addSafe(set, find(app, "java/lang/ClassLoader", "loadClassInternal", "(Ljava/lang/String;)Ljava/lang/Class;"));
		addSafe(set, find(app, "java/lang/ClassLoader", "checkPackageAccess", "(Ljava/lang/Class;Ljava/security/ProtectionDomain;)V"));
		addSafe(set, find(app, "java/lang/ClassLoader", "addClass", "(Ljava/lang/Class;)V"));
		addSafe(set, find(app, "java/lang/ClassLoader", "addClass", "(Ljava/lang/ClassLoader;Ljava/lang/String;)J"));
		
		addSafe(set, find(app, "java/security/PrivilegedActionException", "<init>", "(Ljava/lang/Exception;)V"));
		addSafe(set, find(app, "java/lang/ref/Finalizer", "runFinalizer", "()V"));
		
		addSafe(set, find(app, "java/lang/Thread", "<init>", "(Ljava/lang/ThreadGroup;Ljava/lang/Runnable;)V"));
		addSafe(set, find(app, "java/lang/Thread", "<init>", "(Ljava/lang/ThreadGroup;Ljava/lang/String;)V"));
		
		return set;
	}
	
	private static MethodNode find(ApplicationClassSource app, String owner, String name, String desc) {
		ClassNode cn = app.findClassNode(owner);
		
		for(MethodNode m : cn.methods) {
			if(m.name.equals(name) && m.desc.equals(desc)) {
				return m;
			}
		}
		
		return null;
	}
	
	private static <N> void addSafe(Set<N> set, N n) {
		if(n == null) {
			throw new NullPointerException();
		}
		
		set.add(n);
	}
}