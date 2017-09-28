package org.mapleir.serviceframework.impl;

import org.mapleir.serviceframework.api.IServiceFactory;

public class InternalFactoryServiceReferenceImpl<T> extends AbstractInternalServiceReference<T> {

	private final IServiceFactory<T> factory;
	
	public InternalFactoryServiceReferenceImpl(IServiceFactory<T> factory) {
		this.factory = factory;
	}
	
	@Override
	public T get() {
		return factory.get();
	}
}