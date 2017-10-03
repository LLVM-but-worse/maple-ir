package org.mapleir.propertyframework.api.event.container;

public interface IPropertyRemovedEvent extends IPropertyContainerEvent {

	default Operation getOperation() {
		return Operation.REMOVED;
	}
}