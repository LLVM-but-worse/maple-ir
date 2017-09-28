package org.mapleir.propertyframework.api;

public interface IPropertyDictionary {

	<T> IProperty<T> find(String key);
	
	<T> IProperty<T> find(Class<T> type, String key);
	
	void put(IProperty<?> property);
}