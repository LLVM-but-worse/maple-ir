package org.mapleir.stdlib.collections.map;

public interface ValueCreator<V> extends KeyedValueCreator<Object, V> {
	V create();

	default V create(Object o) {
		return create();
	}
}