package org.mapleir.deob.interproc.callgraph;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class AcyclicSiteSensitiveCallGraph extends CallSiteSensitiveCallGraph {
	// Represents an SCC in the DAG
	public class MultiCallGraphNode extends CallGraphNode implements Collection<CallGraphNode> {
		private final List<CallGraphNode> backingCollection;
		
		public MultiCallGraphNode(int id, List<CallGraphNode> scc) {
			super(id);
			backingCollection = scc;
		}
		
		// CallGraphNode override
		@Override
		public String toString() {
			return backingCollection.toString();
		}
		
		// Delegation methods for collection
		@Override
		public int size() {
			return backingCollection.size();
		}
		
		@Override
		public boolean isEmpty() {
			return backingCollection.isEmpty();
		}
		
		@Override
		public boolean contains(Object o) {
			return backingCollection.contains(o);
		}
		
		@Override
		public Iterator<CallGraphNode> iterator() {
			return backingCollection.iterator();
		}
		
		@Override
		public Object[] toArray() {
			return backingCollection.toArray();
		}
		
		@Override
		public <T> T[] toArray(T[] a) {
			return backingCollection.toArray(a);
		}
		
		@Override
		public boolean add(CallGraphNode callGraphNode) {
			return backingCollection.add(callGraphNode);
		}
		
		@Override
		public boolean remove(Object o) {
			return backingCollection.remove(o);
		}
		
		@Override
		public boolean containsAll(Collection<?> c) {
			return backingCollection.containsAll(c);
		}
		
		@Override
		public boolean addAll(Collection<? extends CallGraphNode> c) {
			return backingCollection.addAll(c);
		}
		
		@Override
		public boolean removeAll(Collection<?> c) {
			return backingCollection.removeAll(c);
		}
		
		@Override
		public boolean retainAll(Collection<?> c) {
			return backingCollection.retainAll(c);
		}
		
		@Override
		public void clear() {
			backingCollection.clear();
		}
	}
}
