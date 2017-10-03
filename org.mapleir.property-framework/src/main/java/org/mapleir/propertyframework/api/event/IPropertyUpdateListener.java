package org.mapleir.propertyframework.api.event;

import org.mapleir.propertyframework.api.event.update.IPropertyUpdateEvent;

public interface IPropertyUpdateListener {
	void onPropertyUpdateEvent(IPropertyUpdateEvent e);
}