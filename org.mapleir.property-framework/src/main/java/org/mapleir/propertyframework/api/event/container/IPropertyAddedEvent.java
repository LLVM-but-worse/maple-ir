package org.mapleir.propertyframework.api.event.container;

public interface IPropertyAddedEvent extends IPropertyContainerEvent {

	default Operation getOperation() {
		return Operation.ADDED;
	}
}