package org.mapleir.stdlib.util;

import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;

// Quick list implementation that just delegates everything to the list returned by getBackingCollection().
public interface DelegatingList<T> extends List<T> {
	List<T> getBackingCollection();
	
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
	default <T1> T1[] toArray(T1[] a) {
		return getBackingCollection().toArray(a);
	}
	
	@Override
	default boolean add(T t) {
		return getBackingCollection().add(t);
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
	default boolean addAll(int index, Collection<? extends T> c) {
		return getBackingCollection().addAll(index, c);
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
	default void replaceAll(UnaryOperator<T> operator) {
		getBackingCollection().replaceAll(operator);
	}
	
	@Override
	default void sort(Comparator<? super T> c) {
		getBackingCollection().sort(c);
	}
	
	@Override
	default void clear() {
		getBackingCollection().clear();
	}

	@Override
	default T get(int index) {
		return getBackingCollection().get(index);
	}
	
	@Override
	default T set(int index, T element) {
		return getBackingCollection().set(index, element);
	}
	
	@Override
	default void add(int index, T element) {
		getBackingCollection().add(index, element);
	}
	
	@Override
	default T remove(int index) {
		return getBackingCollection().remove(index);
	}
	
	@Override
	default int indexOf(Object o) {
		return getBackingCollection().indexOf(o);
	}
	
	@Override
	default int lastIndexOf(Object o) {
		return getBackingCollection().lastIndexOf(o);
	}
	
	@Override
	default ListIterator<T> listIterator() {
		return getBackingCollection().listIterator();
	}
	
	@Override
	default ListIterator<T> listIterator(int index) {
		return getBackingCollection().listIterator(index);
	}
	
	@Override
	default List<T> subList(int fromIndex, int toIndex) {
		return getBackingCollection().subList(fromIndex, toIndex);
	}
	
	@Override
	default Spliterator<T> spliterator() {
		return getBackingCollection().spliterator();
	}
	
	@Override
	default boolean removeIf(Predicate<? super T> filter) {
		return getBackingCollection().removeIf(filter);
	}
	
	@Override
	default Stream<T> stream() {
		return getBackingCollection().stream();
	}
	
	@Override
	default Stream<T> parallelStream() {
		return getBackingCollection().parallelStream();
	}
	
	@Override
	default void forEach(Consumer<? super T> action) {
		getBackingCollection().forEach(action);
	}
}
