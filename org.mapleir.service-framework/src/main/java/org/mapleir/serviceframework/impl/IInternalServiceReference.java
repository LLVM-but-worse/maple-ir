package org.mapleir.serviceframework.impl;

import org.mapleir.serviceframework.api.IServiceReference;

public interface IInternalServiceReference<T> extends IServiceReference<T> {

	T get();
	
	void lock();
	
	void unlock();
	
	boolean locked();
}