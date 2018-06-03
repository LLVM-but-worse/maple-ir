package org.mapleir.stdlib.util;

// todo: tuple class

/**
 * Represents a pair of two objects. Borrowed generously from javafx
 * @param <K> the key (first object)
 * @param <V> the value (second object)
 */
public class Pair<K, V> {
	private K key;
	private V value;

	public Pair(K key, V value) {
		this.key = key;
		this.value = value;
	}

	public K getKey() {
		return key;
	}

	public V getValue() {
		return value;
	}

	@Override
	public String toString() {
		return "(" + key + ", " + value + ")";
	}

	@Override
	public int hashCode() {
		return key.hashCode() * 13 + (value == null ? 0 : value.hashCode());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Pair))
			return false;
		Pair pair = (Pair) o;
		if (key != null ? !key.equals(pair.key) : pair.key != null)
			return false;
		if (value != null ? !value.equals(pair.value) : pair.value != null)
			return false;
		return true;
	}
}
