package org.mapleir.propertyframework.impl.event.container;

import org.mapleir.propertyframework.api.IProperty;
import org.mapleir.propertyframework.api.IPropertyDictionary;
import org.mapleir.propertyframework.api.event.container.IPropertyContainerEvent;
import org.mapleir.propertyframework.impl.event.AbstractPropertyEvent;

public abstract class AbstractPropertyContainerEvent extends AbstractPropertyEvent implements IPropertyContainerEvent {

	private final IPropertyDictionary dictionary;
	private final String key;
	
	public AbstractPropertyContainerEvent(IProperty<?> prop, IPropertyDictionary dictionary, String key) {
		super(prop);
		this.dictionary = dictionary;
		this.key = key;
	}
	
	public IPropertyDictionary getDictionary() {
		return dictionary;
	}
	
	public String getKey() {
		return key;
	}
}