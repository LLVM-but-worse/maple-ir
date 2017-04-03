package org.mapleir.stdlib.collections.nullpermeable;

public interface KeyedValueCreator<K, V> {
	V create(K k);
}
