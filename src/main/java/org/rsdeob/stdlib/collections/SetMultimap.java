package org.rsdeob.stdlib.collections;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class SetMultimap<K, V> implements Multimap<K, V> {
	private final Map<K, Set<V>> map;
	
	public SetMultimap() {
		map = new HashMap<>();
	}
	
	@Override
	public Map<K, Set<V>> asMap() {
		return map;
	}
	
	@Override
	public void clear() {
		map.clear();
	}
	
	@Override
	public boolean containsKey(K key) {
		return map.containsKey(key);
	}
	
	@Override
	public boolean containsValue(V value) {
		for (Map.Entry<K, Set<V>> e : map.entrySet())
			if (e.getValue().contains(value))
				return true;
		return false;
	}
	
	@Override
	public boolean containsMapping(K key, V value) {
		return get(key).contains(value);
	}
	
	@Override
	public Set<Map.Entry<K, V>> entrySet() {
		Set<Map.Entry<K, V>> entrySet = new HashSet<>();
		for (Map.Entry<K, Set<V>> e : map.entrySet())
			for (V v : e.getValue())
				entrySet.add(new AbstractMap.SimpleEntry<>(e.getKey(), v));
		return entrySet;
	}
	
	@Override
	public Set<V> get(K key) {
		if (!map.containsKey(key))
			map.put(key, new HashSet<>());
		return map.get(key);
	}
	
	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}
	
	@Override
	public Set<K> keySet() {
		return map.keySet();
	}
	
	@Override
	public boolean put(K key, V value) {
		return get(key).add(value);
	}
	
	@Override
	public void putAll(Map<? extends K, ? extends V> map) {
		for (Map.Entry<? extends K, ? extends V> e : map.entrySet())
			get(e.getKey()).add(e.getValue());
	}
	
	@Override
	public void putAll(Multimap<? extends K, ? extends V> map) {
		for (Map.Entry<? extends K, ? extends Collection<? extends V>> e : map.asMap().entrySet())
			putAll(e.getKey(), e.getValue());
	}
	
	@Override
	public void putAll(K key, Iterable<? extends V> values) {
		Set<V> set = get(key);
		for (V v : values)
			set.add(v);
	}
	
	@Override
	public Set<V> remove(K key) {
		if (!containsKey(key))
			return null;
		Set<V> values = get(key);
		Set<V> oldValues = new HashSet<>(values);
		values.clear();
		return oldValues;
	}
	
	@Override
	public boolean remove(K key, V value) {
		return get(key).remove(value);
	}
	
	@Override
	public int size() {
		int size = 0;
		for (Map.Entry<K, Set<V>> e : map.entrySet())
			size += e.getValue().size();
		return size;
	}
	
	@Override
	public int keyCount() {
		return map.size();
	}
	
	@Override
	public Collection<V> values() {
		Set<V> values = new HashSet<>();
		for (Map.Entry<K, Set<V>> e : map.entrySet())
			values.addAll(e.getValue());
		return values;
	}
	
	@Override
	public String toString() {
		StringBuilder sb= new StringBuilder();
		sb.append("{");
		Iterator<Map.Entry<K, Set<V>>> it = asMap().entrySet().iterator();
		while(it.hasNext()) {
			Map.Entry<K, Set<V>> e = it.next();
			sb.append("  ").append(e.getKey()).append(" = ").append(e.getValue());
			if(it.hasNext()) {
				sb.append("\n");
			}
		}
		sb.append(" }");
		return sb.toString();
	}
	
	@Override // TODO
	public boolean equals(Object o) {
		throw new NotImplementedException();
	}
}
