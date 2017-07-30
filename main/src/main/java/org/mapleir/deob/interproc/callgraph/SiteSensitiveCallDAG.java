package org.mapleir.deob.interproc.callgraph;

import org.mapleir.stdlib.collections.graph.FastDirectedGraph;
import org.mapleir.stdlib.collections.graph.FastGraph;
import org.mapleir.stdlib.collections.graph.FastGraphEdge;
import org.mapleir.stdlib.util.DelegatingCollection;
import org.mapleir.stdlib.util.DelegatingList;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SiteSensitiveCallDAG extends FastDirectedGraph<SiteSensitiveCallDAG.MultiCallGraphNode, SiteSensitiveCallDAG.SiteSensitiveCallDAGEdge> {
	private final Map<CallGraphNode, MultiCallGraphNode> sccCache;

	public SiteSensitiveCallDAG() {
		sccCache = new HashMap<>();
	}

	public SiteSensitiveCallDAG(SiteSensitiveCallDAG other) {
		super(other);
		sccCache = new HashMap<>(other.sccCache);
	}

	@Override
	public boolean addVertex(MultiCallGraphNode v) {
		for (CallGraphNode endpt : v)
			sccCache.put(endpt, v);
		return super.addVertex(v);
	}

	@Override
	public void removeVertex(MultiCallGraphNode v) {
		for (CallGraphNode endpt : v)
			sccCache.remove(endpt);
		super.removeVertex(v);
	}

	public MultiCallGraphNode findSCCOf(CallGraphNode endpoint) {
		return sccCache.get(endpoint);
	}

	public boolean containsEndpoint(CallGraphNode endpoint) {
		return sccCache.containsKey(endpoint);
	}

	@Override
	public boolean excavate(MultiCallGraphNode n) {
		throw new UnsupportedOperationException("Induced subgraph not supported.");
	}

	@Override
	public boolean jam(MultiCallGraphNode pred, MultiCallGraphNode succ, MultiCallGraphNode n) {
		throw new UnsupportedOperationException("Edge splitting not supported.");
	}

	@Override
	public SiteSensitiveCallDAGEdge clone(SiteSensitiveCallDAGEdge edge, MultiCallGraphNode oldN, MultiCallGraphNode newN) {
		throw new UnsupportedOperationException("Edge cloning not supported.");
	}

	@Override
	public SiteSensitiveCallDAGEdge invert(SiteSensitiveCallDAGEdge edge) {
		throw new UnsupportedOperationException("Edge cloning not supported.");
	}

	@Override
	public FastGraph<MultiCallGraphNode, SiteSensitiveCallDAGEdge> copy() {
		return new SiteSensitiveCallDAG(this);
	}

	// Used for builder.
	private int getNextNodeId() {
		return size() + 1;
	}

	public MultiCallGraphNode createNode(List<CallGraphNode> scc) {
		return new MultiCallGraphNode(getNextNodeId(), scc);
	}

	// Represents an SCC in the DAG
	public class MultiCallGraphNode extends CallGraphNode implements DelegatingList<CallGraphNode> {
		private final List<CallGraphNode> backingCollection;
		
		private MultiCallGraphNode(int id, List<CallGraphNode> scc) {
			super(id);
			backingCollection = scc;
		}
		
		// CallGraphNode override
		@Override
		public String toString() {
			return backingCollection.toString();
		}
		
		@Override
		public List<CallGraphNode> getBackingCollection() {
			return backingCollection;
		}
	}

	public static class SiteSensitiveCallDAGEdge extends FastGraphEdge<MultiCallGraphNode> {
		public SiteSensitiveCallDAGEdge(MultiCallGraphNode src, MultiCallGraphNode dst) {
			super(src, dst);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;

			FastGraphEdge<?> that = (FastGraphEdge<?>) o;

			if (!src.equals(that.src))
				return false;
			return dst.equals(that.dst);
		}

		@Override
		public int hashCode() {
			int result = src.hashCode();
			result = 31 * result + dst.hashCode();
			return result;
		}
	}
}
