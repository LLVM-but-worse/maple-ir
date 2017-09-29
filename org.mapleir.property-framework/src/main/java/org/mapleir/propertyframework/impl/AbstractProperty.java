package org.mapleir.propertyframework.impl;

import org.mapleir.propertyframework.api.IProperty;
import org.mapleir.propertyframework.api.IPropertyDictionary;

public abstract class AbstractProperty<T> implements IProperty<T> {

	private final String key;
	private final Class<T> type;
	
	private IPropertyDictionary container;
	private T value;
	
	public AbstractProperty(String key, Class<T> type) {
		this.key = key;
		this.type = type;
	}

	@Override
	public String getKey() {
		return key;
	}

	@Override
	public Class<T> getType() {
		return type;
	}

	@Override
	public IPropertyDictionary getDictionary() {
		return container;
	}
	
	@Override
	public T getValue() {
		IProperty<T> del = getDelegate();
		if(del != null) {
			return del.getValue();
		} else if(value == null) {
			value = getDefault();
		}
		return value;
	}

	@Override
	public void setValue(T t) {
		IProperty<T> del = getDelegate();
		if(del != null) {
			del.setValue(t);
		} else {
			value = t;
		}
	}
	
	protected IProperty<T> getDelegate() {
		if(container == null) {
			return null;
		}
		
		IProperty<T> prop = container.find(type, key);
		if(prop == null || prop == this) {
			return null;
		} else {
			return prop;
		}
	}
	
	public abstract T getDefault();
}