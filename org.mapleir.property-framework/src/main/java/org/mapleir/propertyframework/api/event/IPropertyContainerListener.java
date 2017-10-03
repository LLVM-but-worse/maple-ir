package org.mapleir.propertyframework.api.event;

import org.mapleir.propertyframework.api.event.container.IPropertyContainerEvent;

public interface IPropertyContainerListener {
	void onPropertyContainerEvent(IPropertyContainerEvent e);
}