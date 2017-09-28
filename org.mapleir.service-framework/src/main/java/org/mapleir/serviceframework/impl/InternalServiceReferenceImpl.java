package org.mapleir.serviceframework.impl;

public class InternalServiceReferenceImpl<T> extends AbstractInternalServiceReference<T> {

	private final T obj;
	
	public InternalServiceReferenceImpl(T obj) {
		this.obj = obj;
	}
	
	@Override
	public T get() {
		return obj;
	}
}