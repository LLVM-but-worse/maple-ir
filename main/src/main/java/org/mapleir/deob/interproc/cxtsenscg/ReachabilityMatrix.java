package org.mapleir.deob.interproc.cxtsenscg;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.mapleir.deob.interproc.geompa.util.ChunkedQueue;
import org.mapleir.deob.interproc.geompa.util.QueueReader;
import org.objectweb.asm.tree.MethodNode;

public class ReachabilityMatrix {

	private final CallGraph callGraph;
	private final Iterator<Edge> edgeSource;

	private final Set<MethodNode> visited = new HashSet<>();

	private final ChunkedQueue<MethodNode> reachables;
	private final QueueReader<MethodNode> reachableListener; // external reader
	private final QueueReader<MethodNode> unprocessedMethods; // our reader

	public ReachabilityMatrix(CallGraph callGraph, Iterator<MethodNode> entryPoints) {
		this.callGraph = callGraph;

		reachables = new ChunkedQueue<>();
		reachableListener = reachables.reader();
		
		addMethods(entryPoints);

		unprocessedMethods = reachables.reader();
		edgeSource = callGraph.listener();
	}

	private void addMethods(Iterator<? extends MethodNode> methods) {
		while (methods.hasNext())
			addMethod(methods.next());
	}

	private void addMethod(MethodNode m) {
		if (visited.add(m)) {
			// just discovered
			reachables.add(m);
		}
	}

	public void update() {
		while (edgeSource.hasNext()) {
			Edge e = edgeSource.next();
			if(visited.contains(e.src())) {
				addMethod(e.tgt());
			}
		}

		while (unprocessedMethods.hasNext()) {
			MethodNode m = unprocessedMethods.next();

			Iterator<Edge> targets = callGraph.edgesOutOf(m);
			for(Edge e = targets.next(); targets.hasNext();) {
				addMethod(e.tgt());
			}
		}
	}

	public QueueReader<MethodNode> listener() {
		return reachableListener.clone();
	}

	public QueueReader<MethodNode> newListener() {
		return reachables.reader();
	}
}
