package org.mapleir.propertyframework.impl.event.container;

import org.mapleir.propertyframework.api.IProperty;
import org.mapleir.propertyframework.api.IPropertyDictionary;
import org.mapleir.propertyframework.api.event.container.IPropertyRemovedEvent;

public class PropertyRemovedEvent extends AbstractPropertyContainerEvent implements IPropertyRemovedEvent {

	public PropertyRemovedEvent(IProperty<?> prop, IPropertyDictionary dictionary, String key) {
		super(prop, dictionary, key);
	}
}