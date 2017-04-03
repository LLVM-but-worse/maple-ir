package org.mapleir.stdlib.collections.nullpermeable;

public class NullCreator<V> implements ValueCreator<V> {

	@Override
	public V create() {
		return null;
	}
}