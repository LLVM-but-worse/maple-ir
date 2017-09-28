package org.mapleir.propertyframework.impl;

public class StringProperty extends AbstractProperty<String> {

	public StringProperty(String key) {
		super(key, String.class);
	}
	
	public StringProperty(String key, String value) {
		super(key, String.class);
		setValue(value);
	}

	@Override
	public String getDefault() {
		return null;
	}
}