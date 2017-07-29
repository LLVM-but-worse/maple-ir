package org.mapleir.deob.interproc.geompa.cg;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.mapleir.deob.interproc.geompa.MapleMethod;
import org.mapleir.deob.interproc.geompa.MapleMethodOrMethodContext;
import org.mapleir.deob.interproc.geompa.util.ChunkedQueue;
import org.mapleir.deob.interproc.geompa.util.QueueReader;
import org.mapleir.ir.code.expr.invoke.Invocation;

public class CallGraph implements Iterable<Edge> {
	protected Set<Edge> edges = new HashSet<>();
	protected ChunkedQueue<Edge> stream = new ChunkedQueue<>();
	protected QueueReader<Edge> reader = stream.reader();
	protected Map<MapleMethodOrMethodContext, Edge> srcMethodToEdge = new HashMap<>();
	protected Map<Invocation, Edge> srcUnitToEdge = new HashMap<>();
	protected Map<MapleMethodOrMethodContext, Edge> tgtToEdge = new HashMap<>();
	protected Edge dummy = new Edge(null, null, null, Kind.INVALID);

	/**
	 * Used to add an edge to the call graph. Returns true iff the edge was not already present.
	 */
	public boolean addEdge(Edge e) {
		if (!edges.add(e))
			return false;
		stream.add(e);
		Edge position = null;

		position = srcUnitToEdge.get(e.srcUnit());
		if (position == null) {
			srcUnitToEdge.put(e.srcUnit(), e);
			position = dummy;
		}
		e.insertAfterByUnit(position);

		position = srcMethodToEdge.get(e.getSrc());
		if (position == null) {
			srcMethodToEdge.put(e.getSrc(), e);
			position = dummy;
		}
		e.insertAfterBySrc(position);

		position = tgtToEdge.get(e.getTgt());
		if (position == null) {
			tgtToEdge.put(e.getTgt(), e);
			position = dummy;
		}
		e.insertAfterByTgt(position);
		return true;
	}

	/**
	 * Removes all outgoing edges that start at the given unit
	 * 
	 * @param u The unit from which to remove all outgoing edges
	 * @return True if at least one edge has been removed, otherwise false
	 */
	public boolean removeAllEdgesOutOf(Invocation u) {
		boolean hasRemoved = false;
		for (QueueReader<Edge> edgeRdr = listener(); edgeRdr.hasNext();) {
			Edge e = edgeRdr.next();
			if (e.srcUnit() == u) {
				removeEdge(e);
				hasRemoved = true;
			}
		}
		return hasRemoved;
	}

	/**
	 * Swaps an invocation statement. All edges that previously went from the given statement to some callee now go from the new statement to the same callee. This method is intended to be used when a Jimple statement is replaced, but the replacement
	 * does not semantically affect the edges.
	 * 
	 * @param out The old statement
	 * @param in The new statement
	 * @return True if at least one edge was affected by this operation
	 */
	public boolean swapEdgesOutOf(Invocation out, Invocation in) {
		boolean hasSwapped = false;
		for (QueueReader<Edge> edgeRdr = listener(); edgeRdr.hasNext();) {
			Edge e = edgeRdr.next();
			if (e.srcUnit() == out) {
				removeEdge(e);
				addEdge(new Edge(e.getSrc(), in, e.getTgt()));
				hasSwapped = true;
			}
		}
		return hasSwapped;
	}

	/**
	 * Removes the edge e from the call graph. Returns true iff the edge was originally present in the call graph.
	 */
	public boolean removeEdge(Edge e) {
		if (!edges.remove(e))
			return false;
		e.remove();

		if (srcUnitToEdge.get(e.srcUnit()) == e) {
			if (e.nextByUnit().srcUnit() == e.srcUnit()) {
				srcUnitToEdge.put(e.srcUnit(), e.nextByUnit());
			} else {
				srcUnitToEdge.put(e.srcUnit(), null);
			}
		}

		if (srcMethodToEdge.get(e.getSrc()) == e) {
			if (e.nextBySrc().getSrc() == e.getSrc()) {
				srcMethodToEdge.put(e.getSrc(), e.nextBySrc());
			} else {
				srcMethodToEdge.put(e.getSrc(), null);
			}
		}

		if (tgtToEdge.get(e.getTgt()) == e) {
			if (e.nextByTgt().getTgt() == e.getTgt()) {
				tgtToEdge.put(e.getTgt(), e.nextByTgt());
			} else {
				tgtToEdge.put(e.getTgt(), null);
			}
		}

		return true;
	}

	/**
	 * Does this method have no incoming edge?
	 * 
	 * @param method
	 * @return
	 */
	public boolean isEntryMethod(MapleMethod method) {
		return !tgtToEdge.containsKey(method);
	}

	/**
	 * Find the specific call edge that is going out from the callsite u and the call target is callee. Without advanced data structure, we can only sequentially search for the match. Fortunately, the number of outgoing edges for a unit is not too large.
	 * 
	 * @param u
	 * @param callee
	 * @return
	 */
	public Edge findEdge(Invocation u, MapleMethod callee) {
		Edge e = srcUnitToEdge.get(u);
		while (e.srcUnit() == u && e.kind() != Kind.INVALID) {
			if (e.tgt() == callee)
				return e;
			e = e.nextByUnit();
		}
		return null;
	}

	/**
	 * Returns an iterator over all methods that are the sources of at least one edge.
	 */
	public Iterator<MapleMethodOrMethodContext> sourceMethods() {
		return srcMethodToEdge.keySet().iterator();
	}

	/** Returns an iterator over all edges that have u as their source unit. */
	public Iterator<Edge> edgesOutOf(Invocation u) {
		return new TargetsOfUnitIterator(u);
	}

	class TargetsOfUnitIterator implements Iterator<Edge> {
		private Edge position = null;
		private Invocation u;

		TargetsOfUnitIterator(Invocation u) {
			this.u = u;
			if (u == null)
				throw new RuntimeException();
			position = srcUnitToEdge.get(u);
			if (position == null)
				position = dummy;
		}

		@Override
		public boolean hasNext() {
			if (position.srcUnit() != u)
				return false;
			if (position.kind() == Kind.INVALID)
				return false;
			return true;
		}

		@Override
		public Edge next() {
			Edge ret = position;
			position = position.nextByUnit();
			return ret;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	/** Returns an iterator over all edges that have m as their source method. */
	public Iterator<Edge> edgesOutOf(MapleMethodOrMethodContext m) {
		return new TargetsOfMethodIterator(m);
	}

	class TargetsOfMethodIterator implements Iterator<Edge> {
		private Edge position = null;
		private MapleMethodOrMethodContext m;

		TargetsOfMethodIterator(MapleMethodOrMethodContext m) {
			this.m = m;
			if (m == null)
				throw new RuntimeException();
			position = srcMethodToEdge.get(m);
			if (position == null)
				position = dummy;
		}

		@Override
		public boolean hasNext() {
			if (position.getSrc() != m)
				return false;
			if (position.kind() == Kind.INVALID)
				return false;
			return true;
		}

		@Override
		public Edge next() {
			Edge ret = position;
			position = position.nextBySrc();
			return ret;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	/** Returns an iterator over all edges that have m as their target method. */
	public Iterator<Edge> edgesInto(MapleMethodOrMethodContext m) {
		return new CallersOfMethodIterator(m);
	}

	class CallersOfMethodIterator implements Iterator<Edge> {
		private Edge position = null;
		private MapleMethodOrMethodContext m;

		CallersOfMethodIterator(MapleMethodOrMethodContext m) {
			this.m = m;
			if (m == null)
				throw new RuntimeException();
			position = tgtToEdge.get(m);
			if (position == null)
				position = dummy;
		}

		@Override
		public boolean hasNext() {
			if (position.getTgt() != m)
				return false;
			if (position.kind() == Kind.INVALID)
				return false;
			return true;
		}

		@Override
		public Edge next() {
			Edge ret = position;
			position = position.nextByTgt();
			return ret;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	/**
	 * Returns a QueueReader object containing all edges added so far, and which will be informed of any new edges that are later added to the graph.
	 */
	public QueueReader<Edge> listener() {
		return reader.clone();
	}

	/**
	 * Returns a QueueReader object which will contain ONLY NEW edges which will be added to the graph.
	 */
	public QueueReader<Edge> newListener() {
		return stream.reader();
	}

	@Override
	public String toString() {
		QueueReader<Edge> reader = listener();
		StringBuffer out = new StringBuffer();
		while (reader.hasNext()) {
			Edge e = (Edge) reader.next();
			out.append(e.toString() + "\n");
		}
		return out.toString();
	}

	/** Returns the number of edges in the call graph. */
	public int size() {
		return edges.size();
	}

	@Override
	public Iterator<Edge> iterator() {
		return edges.iterator();
	}
}
