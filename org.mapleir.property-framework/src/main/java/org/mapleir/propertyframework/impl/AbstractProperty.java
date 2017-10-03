package org.mapleir.propertyframework.impl;

import org.mapleir.propertyframework.api.IProperty;
import org.mapleir.propertyframework.api.IPropertyDictionary;
import org.mapleir.propertyframework.api.event.container.IPropertyContainerEvent;
import org.mapleir.propertyframework.api.event.container.IPropertyContainerEvent.Operation;

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
	
	@Override
	public void onPropertyContainerEvent(IPropertyContainerEvent e) {
		if(e.getProperty() != this) {
			return;
		}
		
		Operation op = e.getOperation();
		if(op == Operation.ADDED) {
			if(container != null) {
				throw new UnsupportedOperationException("Tried to add container-held property to another container");
			} else {
				container = e.getDictionary();
			}
		} else if(op == Operation.REMOVED) {
			if(container != null) {
				throw new UnsupportedOperationException("Tried to remove containerless property from container");
			} else {
				container = null;
			}
		}
	}
	
	public abstract T getDefault();
}