package org.mapleir.deob.interproc.callgraph;

import org.mapleir.ir.code.Expr;
import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.objectweb.asm.tree.MethodNode;

public abstract class CallGraphNode implements FastGraphVertex {

	private final int id;

	public CallGraphNode(int id) {
		this.id = id;
	}

	@Override
	public String getId() {
		return Integer.toString(id);
	}

	@Override
	public int getNumericId() {
		return id;
	}

	@Override
	public abstract String toString();
	
	// A call receiver; e.g. a MethodNode.
	public static class CallReceiverNode extends CallGraphNode {

		private final MethodNode method;
		
		public CallReceiverNode(int id, MethodNode method) {
			super(id);
			this.method = method;
		}

		@Override
		public String toString() {
			return method.toString();
		}
	}
	
	// A call site; e.g. an invocation.
	public static class CallSiteNode extends CallGraphNode {

		private final Expr invoke;
		
		public CallSiteNode(int id, Expr invoke) {
			super(id);
			this.invoke = invoke;
		}

		@Override
		public String toString() {
			MethodNode m = invoke.getBlock().getGraph().getMethod();
			return m.owner + "." + m.name + "@" + invoke.getBlock().indexOf(invoke.getRootParent()) + ":" + invoke.getParent().indexOf(invoke);
		}
	}
}
