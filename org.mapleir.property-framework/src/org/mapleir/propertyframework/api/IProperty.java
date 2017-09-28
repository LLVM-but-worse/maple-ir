package org.mapleir.propertyframework.api;

public interface IProperty<T> {

	String getKey();
	
	Class<T> getType();
	
	IPropertyDictionary getDictionary();
	
	T getValue();
	
	void setValue(T t);
}