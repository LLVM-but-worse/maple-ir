package org.mapleir.stdlib.collections;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;

public class GenericBitSet<N extends BitSetElement> implements Set<N> {
	private BitSet bitset;
	
	public GenericBitSet() {
		bitset = new BitSet();
	}
	
	@Override
	public boolean add(N n) {
		boolean ret = !contains(n);
		bitset.set(n.getIndex());
		return ret;
	}
	
	@Override
	public boolean remove(Object o) {
		if (!(o instanceof BitSetElement))
			return false;
		N n = ((N) o);
		boolean ret = contains(n);
		bitset.set(n.getIndex(), false);
		return ret;
	}
	
	public boolean containsAll(GenericBitSet<N> set) {
		BitSet temp = (BitSet) set.bitset.clone();
		temp.and(set.bitset);
		return temp.equals(set.bitset);
	}
	
	@Override
	public boolean containsAll(Collection<?> c) {
		for (Object o : c)
			if (!contains(o))
				return false;
		return true;
	}
	
	public boolean addAll(GenericBitSet<N> n) {
		BitSet temp = (BitSet) n.bitset.clone();
		temp.or(n.bitset);
		return !bitset.equals(temp);
	}
	
	@Override
	public boolean addAll(Collection<? extends N> c) {
		boolean ret = false;
		for (Object o : c)
			if (o instanceof BitSetElement)
				ret = ret || add((N) o);
		return ret;
	}
	
	public boolean retainAll(GenericBitSet<N> n) {
		BitSet temp = (BitSet) n.bitset.clone();
		temp.and(n.bitset);
		return !bitset.equals(temp);
	}
	
	@Override
	public boolean retainAll(Collection<?> c) {
		boolean ret = false;
		Iterator<N> it = iterator();
		while (it.hasNext()) {
			if (!c.contains(it.next())) {
				it.remove();
				ret = true;
			}
		}
		return ret;
	}
	
	public boolean removeAll(GenericBitSet<N> n) {
		BitSet temp = (BitSet) n.bitset.clone();
		temp.andNot(n.bitset);
		return !bitset.equals(temp);
	}
	
	@Override
	public boolean removeAll(Collection<?> c) {
		boolean ret = false;
		for (Object o : c)
			if (o instanceof BitSetElement)
				ret = ret || remove((N) o);
		return ret;
	}
	
	@Override
	public void clear() {
		bitset.clear();
	}
	
	@Override
	public int size() {
		return bitset.size();
	}
	
	@Override
	public boolean isEmpty() {
		return bitset.isEmpty();
	}
	
	@Override
	public boolean contains(Object o) {
		if (!(o instanceof BitSetElement))
			return false;
		return bitset.get(((N) o).getIndex());
	}
	
	@Override
	public Iterator<N> iterator() {
		return new Iterator<N> () {
			int index = 0;
			
			@Override
			public boolean hasNext() {
				return bitset.nextSetBit(index) != -1;
			}
			
			@Override
			public N next() {
				return bitset.get(bitset.nextSetBit(index));
			}
		}
	}
	
	@Override
	public Spliterator<N> spliterator() {
		throw new NotImplementedException();
	}
	
	@Override
	public Object[] toArray() {
		throw new NotImplementedException();
	}
	
	@Override
	public <T> T[] toArray(T[] a) {
		throw new NotImplementedException();
	}
}
