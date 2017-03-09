package org.mapleir.stdlib.call;

import org.mapleir.ir.code.Expr;
import org.mapleir.stdlib.app.ApplicationClassSource;
import org.mapleir.stdlib.klass.InvocationResolver;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashSet;
import java.util.Set;

public abstract class CallTracer {

	protected final ApplicationClassSource tree;
	protected final InvocationResolver resolver;
	
	private final Set<MethodNode> visited;
	
	public CallTracer(ApplicationClassSource tree, InvocationResolver resolver) {
		this.tree = tree;
		this.resolver = resolver;
		
		visited = new HashSet<>();
	}
	
	public void trace(MethodNode m) {
		if(visited.contains(m)) {
			return;
		} else {
			visited.add(m);
			visitMethod(m);
			
			if(tree.isLibraryClass(m.owner.name)) {
				return;
			}
		}
		
		traceImpl(m);
	}
	
	protected abstract void traceImpl(MethodNode m);
	
	protected void visitMethod(MethodNode m) {}
	
	protected void processedInvocation(MethodNode caller, MethodNode callee, Expr call) {}
}