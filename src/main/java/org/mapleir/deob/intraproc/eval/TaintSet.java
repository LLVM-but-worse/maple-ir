package org.mapleir.deob.intraproc.eval;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class TaintSet<T> implements Set<T> {

	private final Set<T> backingSet;
	private boolean tainted;
	
	public TaintSet() {
		backingSet = new HashSet<>();
	}
	
	@Override
	public int size() {
		return backingSet.size();
	}

	@Override
	public boolean isEmpty() {
		return backingSet.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		return backingSet.contains(o);
	}

	@Override
	public Iterator<T> iterator() {
		return backingSet.iterator();
	}

	@Override
	public Object[] toArray() {
		return backingSet.toArray();
	}

	@Override
	public <T2> T2[] toArray(T2[] a) {
		return backingSet.toArray(a);
	}

	@Override
	public boolean add(T e) {
		return backingSet.add(e);
	}

	@Override
	public boolean remove(Object o) {
		return backingSet.remove(o);
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean addAll(Collection<? extends T> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void clear() {
		// TODO Auto-generated method stub
		
	}

}