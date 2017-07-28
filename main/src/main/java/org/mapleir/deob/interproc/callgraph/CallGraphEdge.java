package org.mapleir.deob.interproc.callgraph;

import org.mapleir.stdlib.collections.graph.FastGraphEdge;

public abstract class CallGraphEdge extends FastGraphEdge<CallGraphNode> {
	public CallGraphEdge(CallGraphNode src, CallGraphNode dst) {
		super(src, dst);
	}

	public abstract boolean canClone(CallGraphNode src, CallGraphNode dst);

	public abstract CallGraphEdge clone(CallGraphNode src, CallGraphNode dst);
	
	// The source receiver (method) contains the destination call site (invocation).
	public static class FunctionOwnershipEdge extends CallGraphEdge {
		public FunctionOwnershipEdge(CallGraphNode.CallReceiverNode src, CallGraphNode.CallSiteNode dst) {
			super(src, dst);
		}

		@Override
		public boolean canClone(CallGraphNode src, CallGraphNode dst) {
			return src instanceof CallGraphNode.CallReceiverNode && dst instanceof CallGraphNode.CallSiteNode;
		}

		@Override
		public CallGraphEdge clone(CallGraphNode src, CallGraphNode dst) {
			return new FunctionOwnershipEdge((CallGraphNode.CallReceiverNode) src, (CallGraphNode.CallSiteNode) dst);
		}
	}
	
	// The source call site (invocation) resolves to the destination method (receiver).
	public static class SiteInvocationEdge extends CallGraphEdge {

		public SiteInvocationEdge(CallGraphNode.CallSiteNode src, CallGraphNode.CallReceiverNode dst) {
			super(src, dst);
		}

		@Override
		public boolean canClone(CallGraphNode src, CallGraphNode dst) {
			return src instanceof CallGraphNode.CallSiteNode && dst instanceof CallGraphNode.CallReceiverNode;
		}

		@Override
		public CallGraphEdge clone(CallGraphNode src, CallGraphNode dst) {
			return new SiteInvocationEdge((CallGraphNode.CallSiteNode) src, (CallGraphNode.CallReceiverNode) dst);
		}
		
	}
}
