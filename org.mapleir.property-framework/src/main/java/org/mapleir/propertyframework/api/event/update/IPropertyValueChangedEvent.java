package org.mapleir.propertyframework.api.event.update;

public interface IPropertyValueChangedEvent extends IPropertyUpdateEvent {

	<T> T getOldValue();
	
	<T> T getNewValue();
}