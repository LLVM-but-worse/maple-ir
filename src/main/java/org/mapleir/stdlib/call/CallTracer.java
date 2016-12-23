package org.mapleir.stdlib.call;

import java.util.HashSet;
import java.util.Set;

import org.mapleir.ir.code.Expr;
import org.mapleir.stdlib.klass.ClassTree;
import org.mapleir.stdlib.klass.InvocationResolver;
import org.objectweb.asm.tree.MethodNode;

public abstract class CallTracer {

	protected final ClassTree tree;
	protected final InvocationResolver resolver;
	
	private final Set<MethodNode> visited;
	
	public CallTracer(ClassTree tree, InvocationResolver resolver) {
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
			
			if(tree.isJDKClass(m.owner)) {
				return;
			}
		}
		
		traceImpl(m);
	}
	
	protected abstract void traceImpl(MethodNode m);
	
	protected void visitMethod(MethodNode m) {}
	
	protected void processedInvocation(MethodNode caller, MethodNode callee, Expr call) {}
}