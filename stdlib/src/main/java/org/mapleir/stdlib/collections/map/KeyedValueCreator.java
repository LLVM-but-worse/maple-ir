package org.mapleir.stdlib.collections.map;

import java.util.HashMap;
import java.util.Map;

public interface KeyedValueCreator<K, V> {
	V create(K k);
	
	public static abstract class CachedKeyedValueCreator<K, V> implements KeyedValueCreator<K, V> {
		private final Map<K, V> map = makeMapImpl();
		
		protected Map<K, V> makeMapImpl() {
			return new HashMap<>();
		}
		
		public Map<K, V> getMap() {
			return map;
		}

		protected abstract V create0(K k);
		
		@Override
		public V create(K k) {
			if(map.containsKey(k)) {
				return map.get(k);
			} else {
				V v = create0(k);
				map.put(k, v);
				return v;
			}
		}
	}
}
