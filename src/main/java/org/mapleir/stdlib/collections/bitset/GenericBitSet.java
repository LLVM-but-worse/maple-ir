package org.mapleir.stdlib.collections.bitset;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;

public class GenericBitSet<N> implements Set<N> {
	private BitSet bitset;
	private BitSetIndexer<N> indexer;
	
	public GenericBitSet(BitSetIndexer<N> indexer) {
		bitset = new BitSet();
		this.indexer = indexer;
	}
	
	public GenericBitSet(GenericBitSet<N> other) {
		indexer = other.indexer;
		bitset = (BitSet) other.bitset.clone();
	}
	
	public GenericBitSet<N> copy() {
		return new GenericBitSet<>(this);
	}
	
	@Override
	public boolean add(N n) {
		boolean ret = !contains(n);
		bitset.set(indexer.getIndex(n));
		return ret;
	}
	
	@Override
	public boolean remove(Object o) {
		boolean ret = contains(o);
		if (!ret)
			return false;
		bitset.set(indexer.getIndex((N) o), false);
		return ret;
	}
	
	public boolean containsAll(GenericBitSet<N> set) {
		BitSet temp = (BitSet) bitset.clone(); // if contains all, set.bitset will be a subset of our bitset
		temp.and(set.bitset);
		return temp.equals(bitset);
	}
	
	@Override
	public boolean containsAll(Collection<?> c) {
		for (Object o : c)
			if (!contains(o))
				return false;
		return true;
	}

	public boolean containsNone(GenericBitSet<N> set) {
		BitSet temp = (BitSet) bitset.clone();
		temp.and(set.bitset);
		return temp.isEmpty();
	}

	public boolean containsAny(GenericBitSet<N> set) {
		return !containsNone(set);
	}
	
	public void addAll(GenericBitSet<N> n) {
		if (indexer != n.indexer)
			throw new IllegalArgumentException("Fast addAll operands must share the same BitSetIndexer");
		bitset.or(n.bitset);
	}
	
	public GenericBitSet<N> union(GenericBitSet<N> n) {
		GenericBitSet<N> copy = copy();
		copy.addAll(n);
		return copy;
	}
	
	@Override
	public boolean addAll(Collection<? extends N> c) {
		boolean ret = false;
		for (N o : c)
			ret = add(o) || ret;
		return ret;
	}
	
	public void retainAll(GenericBitSet<N> n) {
		bitset.and(n.bitset);
	}
	
	public GenericBitSet<N> intersect(GenericBitSet<N> n) {
		GenericBitSet<N> copy = copy();
		copy.retainAll(n);
		return copy;
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
	
	public void removeAll(GenericBitSet<N> n) {
		bitset.andNot(n.bitset);
	}
	
	public GenericBitSet<N> relativeComplement(GenericBitSet<N> n) {
		GenericBitSet<N> copy = copy();
		copy.removeAll(n);
		return copy;
	}

	public GenericBitSet<N> relativeComplement(N n) {
		GenericBitSet<N> copy = copy();
		copy.remove(n);
		return copy;
	}
	
	@Override
	public boolean removeAll(Collection<?> c) {
		boolean ret = false;
		for (Object o : c)
			ret = remove(o) || ret;
		return ret;
	}
	
	@Override
	public void clear() {
		bitset.clear();
	}
	
	@Override
	public int size() {
		return bitset.cardinality();
	}
	
	@Override
	public boolean isEmpty() {
		return bitset.isEmpty();
	}
	
	@Override
	public boolean contains(Object o) {
		if (!indexer.isIndexed(o))
			return false;
		return bitset.get(indexer.getIndex((N) o));
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("[ ");
		for (N n : this)
			sb.append(n).append(" ");
		return sb.append("]").toString();
	}
	
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof GenericBitSet))
			return false;
		GenericBitSet gbs = (GenericBitSet) o;
		if (indexer != gbs.indexer)
			return false;
		return bitset.equals(gbs.bitset);
	}
	
	@Override
	public Iterator<N> iterator() {
		return new Iterator<N> () {
			int index = 0;
			
			@Override
			public boolean hasNext() {
				return bitset.nextSetBit(index + 1) != -1;
			}
			
			@Override
			public N next() {
				return indexer.get(index = bitset.nextSetBit(index + 1));
			}
			
			@Override
			public void remove() {
				 bitset.set(index, false);
			}
		};
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
