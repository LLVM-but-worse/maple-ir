package org.mapleir.deob.interproc.geompa.cg;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.mapleir.deob.interproc.geompa.MapleMethodOrMethodContext;
import org.mapleir.deob.interproc.geompa.util.ChunkedQueue;
import org.mapleir.deob.interproc.geompa.util.QueueReader;

public class ReachableMethods {
	private CallGraph cg;
	private Iterator<Edge> edgeSource;
	private final ChunkedQueue<MapleMethodOrMethodContext> reachables = new ChunkedQueue<>();
	private final Set<MapleMethodOrMethodContext> set = new HashSet<>();
	private QueueReader<MapleMethodOrMethodContext> unprocessedMethods;
	private final QueueReader<MapleMethodOrMethodContext> allReachables = reachables.reader();
	private Filter filter;

	public ReachableMethods(CallGraph graph, Iterator<? extends MapleMethodOrMethodContext> entryPoints) {
		this(graph, entryPoints, null);
	}

	public ReachableMethods(CallGraph graph, Iterator<? extends MapleMethodOrMethodContext> entryPoints, Filter filter) {
		this.filter = filter;
		cg = graph;
		addMethods(entryPoints);
		unprocessedMethods = reachables.reader();
		edgeSource = graph.listener();
		if (filter != null)
			edgeSource = filter.wrap(edgeSource);
	}

	public ReachableMethods(CallGraph graph, Collection<? extends MapleMethodOrMethodContext> entryPoints) {
		this(graph, entryPoints.iterator());
	}

	private void addMethods(Iterator<? extends MapleMethodOrMethodContext> methods) {
		while (methods.hasNext())
			addMethod(methods.next());
	}

	private void addMethod(MapleMethodOrMethodContext m) {
		if (set.add(m)) {
			reachables.add(m);
		}
	}

	/**
	 * Causes the QueueReader objects to be filled up with any methods that have become reachable since the last call.
	 */
	public void update() {
		while (edgeSource.hasNext()) {
			Edge e = edgeSource.next();
			if (set.contains(e.getSrc()))
				addMethod(e.getTgt());
		}
		while (unprocessedMethods.hasNext()) {
			MapleMethodOrMethodContext m = unprocessedMethods.next();
			Iterator<Edge> targets = cg.edgesOutOf(m);
			if (filter != null)
				targets = filter.wrap(targets);
			addMethods(new Targets(targets));
		}
	}

	/**
	 * Returns a QueueReader object containing all methods found reachable so far, and which will be informed of any new methods that are later found to be reachable.
	 */
	public QueueReader<MapleMethodOrMethodContext> listener() {
		return allReachables.clone();
	}

	/**
	 * Returns a QueueReader object which will contain ONLY NEW methods which will be found to be reachable, but not those that have already been found to be reachable.
	 */
	public QueueReader<MapleMethodOrMethodContext> newListener() {
		return reachables.reader();
	}

	/** Returns true iff method is reachable. */
	public boolean contains(MapleMethodOrMethodContext m) {
		return set.contains(m);
	}

	/** Returns the number of methods that are reachable. */
	public int size() {
		return set.size();
	}
}