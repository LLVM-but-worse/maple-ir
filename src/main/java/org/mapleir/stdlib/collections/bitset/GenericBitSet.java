package org.mapleir.stdlib.collections.bitset;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;

public class GenericBitSet<N> implements Set<N> {
	private BitSet bitset;
	private BitSetIndexer<N> indexer;
	
	public GenericBitSet(BitSetIndexer<N> indexer) {
		bitset = new BitSet();
		this.indexer = indexer;
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
		if (indexer != n.indexer)
			throw new IllegalArgumentException("Fast addAll operands must share the same BitSetIndexer");
		BitSet temp = (BitSet) n.bitset.clone();
		temp.or(n.bitset);
		return !bitset.equals(temp);
	}
	
	@Override
	public boolean addAll(Collection<? extends N> c) {
		boolean ret = false;
		for (N o : c)
			ret = ret || add(o);
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
			ret = ret || remove(o);
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
	public Iterator<N> iterator() {
		return new Iterator<N> () {
			int index = 0;
			
			@Override
			public boolean hasNext() {
				return bitset.nextSetBit(index) != -1;
			}
			
			@Override
			public N next() {
				return indexer.get(index = bitset.nextSetBit(index));
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
