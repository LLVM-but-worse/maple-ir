package org.mapleir.deob.interproc.geompa.cg;

import java.util.Iterator;

public class Filter implements Iterator<Edge> {
	private Iterator<Edge> source;
	private EdgePredicate pred;
	private Edge next = null;

	public Filter(EdgePredicate pred) {
		this.pred = pred;
	}

	public Iterator<Edge> wrap(Iterator<Edge> source) {
		this.source = source;
		advance();
		return this;
	}

	private void advance() {
		while (source.hasNext()) {
			next = (Edge) source.next();
			if (pred.want(next)) {
				return;
			}
		}
		next = null;
	}

	@Override
	public boolean hasNext() {
		return next != null;
	}

	@Override
	public Edge next() {
		Edge ret = next;
		advance();
		return ret;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}
}