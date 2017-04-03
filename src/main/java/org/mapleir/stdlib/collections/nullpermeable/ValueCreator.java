package org.mapleir.stdlib.collections.nullpermeable;

public interface ValueCreator<V> extends KeyedValueCreator<Object, V> {
	V create();

	default V create(Object o) {
		return create();
	}
}