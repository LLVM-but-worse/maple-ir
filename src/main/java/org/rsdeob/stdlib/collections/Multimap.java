package org.rsdeob.stdlib.collections;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface Multimap<K, V> {
	Map<K, ? extends Collection<V>> asMap();
	
	void clear();
	
	boolean containsKey(K key);
	
	boolean containsValue(V value);
	
	boolean containsMapping(K key, V value);
	
	Set<Map.Entry<K, V>> entrySet();
	
	Collection<V> get(K key);
	
	boolean isEmpty();
	
	Set<K> keySet();
	
	boolean put(K key, V value);
	
	void putAll(Map<? extends K, ? extends V> map);
	
	void putAll(Multimap<? extends K, ? extends V> map);
	
	void putAll(K k, Iterable<? extends V> values);
	
	Collection<V> remove(K key);
	
	boolean remove(K key, V value);
	
	/**
	 * Returns the total number of values stored in this map.
	 * @return total number of values stored in this map.d
	 */
	int size();
	
	int keyCount();
	
	Collection<V> values();
	
	class MultimapEntry<K, V> extends AbstractMap.SimpleEntry<K, V> {
		Map<K, Collection<V>> backingMap;
		
		public MultimapEntry(K key, V value, Map<K, Collection<V>> map) {
			super(key, value);
			backingMap = map;
		}
		
		@Override
		public V setValue(V value) {
			V oldValue = super.setValue(value);
			Collection<V> values = backingMap.get(getKey());
			values.remove(oldValue);
			values.add(value);
			return oldValue;
		}
	}
}
