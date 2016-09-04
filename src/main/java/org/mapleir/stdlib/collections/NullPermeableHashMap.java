package org.mapleir.stdlib.collections;

import java.util.HashMap;

public class NullPermeableHashMap<K, V> extends HashMap<K, V> {

	private static final long serialVersionUID = 1L;

	private final KeyedValueCreator<? super K, V> creator;

	public NullPermeableHashMap(NullPermeableHashMap<K, V> map) {
		super(map);
		creator = map.creator;
	}
	
	public NullPermeableHashMap(KeyedValueCreator<? super K, V> creator) {
		this.creator = creator;
	}

	public NullPermeableHashMap() {
		this(new NullCreator<>());
	}
	
	public V getNonNull(K k) {
		V val = get(k);
		if (val == null) {
			val = creator.create(k);
			put(k, val);
		} 
		return val;
	}
}