package org.mapleir.propertyframework.impl.event.update;

import org.mapleir.propertyframework.api.IProperty;
import org.mapleir.propertyframework.api.event.update.IPropertyUpdateEvent;
import org.mapleir.propertyframework.impl.event.AbstractPropertyEvent;

public abstract class AbstractPropertyUpdateEvent extends AbstractPropertyEvent implements IPropertyUpdateEvent {

	public AbstractPropertyUpdateEvent(IProperty<?> prop) {
		super(prop);
	}
}