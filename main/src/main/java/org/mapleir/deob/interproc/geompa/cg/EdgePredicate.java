package org.mapleir.deob.interproc.geompa.cg;

public interface EdgePredicate {
	/** Returns true iff the edge e is wanted. */
	public boolean want(Edge e);
}
