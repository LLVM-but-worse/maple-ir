package org.mapleir.propertyframework.impl;

import java.util.HashMap;
import java.util.Map;

import org.mapleir.propertyframework.api.IProperty;
import org.mapleir.propertyframework.api.IPropertyDictionary;
import org.mapleir.propertyframework.util.PropertyHelper;

public class BasicPropertyDictionary implements IPropertyDictionary {

	private final Map<String, IProperty<?>> map = new HashMap<>();

	@SuppressWarnings("unchecked")
	@Override
	public <T> IProperty<T> find(String key) {
		if (!map.containsKey(key)) {
			return null;
		}

		IProperty<?> prop = map.get(key);
		return (IProperty<T>) prop;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> IProperty<T> find(Class<T> type, String key) {
		if (!map.containsKey(key)) {
			return null;
		}

		IProperty<?> prop = map.get(key);
		if (type == null || type.isAssignableFrom(prop.getType())) {
			return (IProperty<T>) prop;
		} else {
			Class<?> rebasedType = PropertyHelper.rebasePrimitiveType(type);
			if(prop.getType().equals(rebasedType)) {
				return (IProperty<T>) prop;
			} else {
				/* New specification compliant: see IPropertyDictionary.find(Class, Key) docs
				 * throw new IllegalStateException(String.format("Cannot coerce %s to %s",
				 *    prop.getType(), type)); */
				return null;
			}
		}
	}

	@Override
	public void put(String key, IProperty<?> property) {
		if (key == null || property == null) {
			throw new NullPointerException(String.format("Cannot map %s to %s", key, property));
		}

		map.put(key, property);
	}
}