package org.mapleir.stdlib.collections.itertools;

import org.mapleir.stdlib.collections.Pair;

import java.util.Iterator;

public class ProductIterator<T> implements Iterator<Pair<T>> {
	private Iterator<T> iteratorA;
	private Iterator<T> iteratorB;
	
	private T curA;
	private Iterable<T> b;
	
	public ProductIterator(Iterable<T> a, Iterable<T> b) {
		this.iteratorA = a.iterator();
		this.iteratorB = b.iterator();
		
		this.curA = null;
		this.b = b;
	}
	
	@Override
	public boolean hasNext() {
		if (!iteratorB.hasNext()) {
			if (!iteratorA.hasNext()) {
				return false; // at the corner; end
			}
			// at end of row; start over B and move to next A
			curA = iteratorA.next();
			iteratorB = b.iterator();
		}
		return true;
	}
	
	@Override
	public Pair<T> next() {
		if (curA == null)
			curA = iteratorA.next();
		return new Pair<>(curA, iteratorB.next());
	}
}
