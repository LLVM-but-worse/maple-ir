package org.mapleir.deob.interproc.geompa.cg;

import java.util.Iterator;

import org.mapleir.deob.interproc.geompa.MapleMethodOrMethodContext;

public final class Targets implements Iterator<MapleMethodOrMethodContext> {
	Iterator<Edge> edges;

	public Targets(Iterator<Edge> edges) {
		this.edges = edges;
	}

	@Override
	public boolean hasNext() {
		return edges.hasNext();
	}

	@Override
	public MapleMethodOrMethodContext next() {
		Edge e = edges.next();
		return e.getTgt();
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}
}