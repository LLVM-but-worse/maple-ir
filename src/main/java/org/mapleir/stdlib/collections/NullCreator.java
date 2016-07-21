package org.mapleir.stdlib.collections;

public class NullCreator<V> implements ValueCreator<V> {

	@Override
	public V create() {
		return null;
	}
}