package org.mapleir.propertyframework.impl.event.update;

import org.mapleir.propertyframework.api.IProperty;
import org.mapleir.propertyframework.api.event.update.IPropertyValueChangedEvent;

public class PropertyValueChangedEvent extends AbstractPropertyUpdateEvent implements IPropertyValueChangedEvent {

	private final Object oldValue;
	private final Object newValue;
	
	public PropertyValueChangedEvent(IProperty<?> prop, Object oldValue, Object newValue) {
		super(prop);
		this.oldValue = oldValue;
		this.newValue = newValue;
	}

	// FIXME:
	/* trust the client with these?
	 *  - alternative: pass type arg */
	
	@SuppressWarnings("unchecked")
	public <T> T getOldValue() {
		return (T) oldValue;
	}

	@SuppressWarnings("unchecked")
	public <T> T getNewValue() {
		return (T) newValue;
	}
}