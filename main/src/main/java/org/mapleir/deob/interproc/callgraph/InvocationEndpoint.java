package org.mapleir.deob.interproc.callgraph;

import org.mapleir.stdlib.collections.graph.FastGraphVertex;

public interface InvocationEndpoint {
	CallGraphNode.CallReceiverNode getReceiver();
	
	CallGraphNode.CallSiteNode getSite();
	
}
