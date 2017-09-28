package org.mapleir.propertyframework.impl;

public class DefaultValueProperty<T> extends AbstractProperty<T> {

	private final T dflt;
	
	public DefaultValueProperty(String key, Class<T> type, T dflt) {
		super(key, type);
		this.dflt = dflt;
	}

	@Override
	public T getDefault() {
		return dflt;
	}
}