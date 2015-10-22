package org.rsdeob.stdlib.collections;

import java.util.HashMap;

public class NullPermeableHashMap<K, V> extends HashMap<K, V> {

	private static final long serialVersionUID = 1L;

	private final ValueCreator<V> creator;

	public NullPermeableHashMap(ValueCreator<V> creator) {
		this.creator = creator;
	}

	public NullPermeableHashMap() {
		this(new NullCreator<V>());
	}

	public V getNonNull(K k) {
		V val = get(k);
		if (val == null) {
			val = creator.create();
			put(k, val);
		} 
		return val;
	}
}