package org.mapleir.propertyframework.impl.event.container;

import org.mapleir.propertyframework.api.IProperty;
import org.mapleir.propertyframework.api.IPropertyDictionary;
import org.mapleir.propertyframework.api.event.container.IPropertyAddedEvent;

public class PropertyAddedEvent extends AbstractPropertyContainerEvent implements IPropertyAddedEvent {

	public PropertyAddedEvent(IProperty<?> prop, IPropertyDictionary dictionary, String key) {
		super(prop, dictionary, key);
	}
}