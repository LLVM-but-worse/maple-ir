package org.mapleir.stdlib.collections;

public interface KeyedValueCreator<K, V> {
	V create(K k);
}
