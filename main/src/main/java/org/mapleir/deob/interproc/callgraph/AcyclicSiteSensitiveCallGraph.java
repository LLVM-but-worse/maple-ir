package org.mapleir.deob.interproc.callgraph;

import org.mapleir.stdlib.util.DelegatingCollection;

import java.util.Collection;
import java.util.List;

public class AcyclicSiteSensitiveCallGraph extends CallSiteSensitiveCallGraph {
	// Represents an SCC in the DAG
	public class MultiCallGraphNode extends CallGraphNode implements DelegatingCollection<InvocationEndpoint> {
		private final List<InvocationEndpoint> backingCollection;
		
		public MultiCallGraphNode(int id, List<InvocationEndpoint> scc) {
			super(id);
			backingCollection = scc;
		}
		
		// CallGraphNode override
		@Override
		public String toString() {
			return backingCollection.toString();
		}
		
		@Override
		public Collection<InvocationEndpoint> getBackingCollection() {
			return backingCollection;
		}
	}
}
