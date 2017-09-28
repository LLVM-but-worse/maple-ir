package org.mapleir.propertyframework.impl;

import java.util.HashMap;
import java.util.Map;

import org.mapleir.propertyframework.api.IProperty;
import org.mapleir.propertyframework.api.IPropertyDictionary;

public class PropertyDictionaryImpl implements IPropertyDictionary {
	
	private final Map<String, IProperty<?>> map = new HashMap<>();
	
	@SuppressWarnings("unchecked")
	@Override
	public <T> IProperty<T> find(String key) {
		if(!map.containsKey(key)) {
			return null;
		}
		
		IProperty<?> prop = map.get(key);
		return (IProperty<T>) prop;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public <T> IProperty<T> find(Class<T> type, String key) {
		if(!map.containsKey(key)) {
			return null;
		}
		
		IProperty<?> prop = map.get(key);
		if(type.isAssignableFrom(prop.getType())) {
			return (IProperty<T>) prop;
		} else {
			throw new IllegalStateException(String.format("Cannot coerce %s to %s", prop.getType(), type));
		}
	}

	@Override
	public void put(IProperty<?> property) {
		if(property == null) {
			throw new IllegalArgumentException();
		}
		map.put(property.getKey(), property);
	}
}