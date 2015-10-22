package org.rsdeob.stdlib.collections;

import java.util.LinkedHashMap;

/**
 * @author Bibl (don't ban me pls)
 * @created 2 Jun 2015 18:47:27
 */
public class NullPermeableLinkedHashMap<K, V> extends LinkedHashMap<K, V> {

	private static final long serialVersionUID = 1L;

	private final ValueCreator<V> creator;

	public NullPermeableLinkedHashMap(ValueCreator<V> creator) {
		this.creator = creator;
	}

	public NullPermeableLinkedHashMap() {
		this(new NullCreator<V>());
	}

	public V getNotNull(K k) {
		V val = get(k);
		if (val == null) {
			val = creator.create();
			put(k, val);
		} 
		return val;
	}
}