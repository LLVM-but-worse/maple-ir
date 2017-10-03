package org.mapleir.propertyframework.api.event.container;

import org.mapleir.propertyframework.api.IPropertyDictionary;
import org.mapleir.propertyframework.api.event.IPropertyEvent;

public interface IPropertyContainerEvent extends IPropertyEvent {
	
	public enum Operation {
		ADDED, REMOVED
	}

	 IPropertyDictionary getDictionary();
	 
	 Operation getOperation();
}