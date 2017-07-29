package org.mapleir.stdlib.util;

import java.util.Collection;
import java.util.Iterator;

// Quick collection implementation that just delegates everything to the collection returned by getBackingCollection().
public interface DelegatingCollection<T> extends Collection<T> {
	Collection<T> getBackingCollection();
	
	@Override
	default int size() {
		return getBackingCollection().size();
	}
	
	@Override
	default boolean isEmpty() {
		return getBackingCollection().isEmpty();
	}
	
	@Override
	default boolean contains(Object o) {
		return getBackingCollection().contains(o);
	}
	
	@Override
	default Iterator<T> iterator() {
		return getBackingCollection().iterator();
	}
	
	@Override
	default Object[] toArray() {
		return getBackingCollection().toArray();
	}
	
	@Override
	default <U> U[] toArray(U[] a) {
		return getBackingCollection().toArray(a);
	}
	
	@Override
	default boolean add(T callGraphNode) {
		return getBackingCollection().add(callGraphNode);
	}
	
	@Override
	default boolean remove(Object o) {
		return getBackingCollection().remove(o);
	}
	
	@Override
	default boolean containsAll(Collection<?> c) {
		return getBackingCollection().containsAll(c);
	}
	
	@Override
	default boolean addAll(Collection<? extends T> c) {
		return getBackingCollection().addAll(c);
	}
	
	@Override
	default boolean removeAll(Collection<?> c) {
		return getBackingCollection().removeAll(c);
	}
	
	@Override
	default boolean retainAll(Collection<?> c) {
		return getBackingCollection().retainAll(c);
	}
	
	@Override
	default void clear() {
		getBackingCollection().clear();
	}
}
