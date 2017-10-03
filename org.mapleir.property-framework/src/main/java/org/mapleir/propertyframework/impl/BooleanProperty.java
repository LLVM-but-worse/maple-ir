package org.mapleir.propertyframework.impl;

public class BooleanProperty extends DefaultValueProperty<Boolean> {

	public BooleanProperty(String key) {
		this(key, false);
	}
	
	public BooleanProperty(String key, boolean dflt) {
		super(key, Boolean.class, dflt);
	}
}