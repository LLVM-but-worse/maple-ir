package org.mapleir.serviceframework.impl;

import org.mapleir.serviceframework.api.IServiceQuery;
import org.mapleir.serviceframework.api.IServiceReference;
import org.mapleir.serviceframework.api.IServiceRegistry;

public class ClassServiceQuery<T> implements IServiceQuery<T> {

	private final Class<T> clazz;
	
	public ClassServiceQuery(Class<T> clazz) {
		this.clazz = clazz;
	}

	@Override
	public boolean accept(IServiceRegistry reg, Class<T> clazz, IServiceReference<T> ref) {
		return this.clazz == clazz;
	}
}