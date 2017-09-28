package org.mapleir.serviceframework.api;

import java.util.Collection;

public interface IServiceRegistry {

	<T> IServiceReference<T> getServiceReference(Class<T> clazz);
	
	<T> Collection<IServiceReference<T>> getServiceReferences(Class<T> clazz, IServiceQuery<T> query);
	
	<T> void registerService(Class<T> clazz, T obj);
	
	<T> void registerServiceFactory(Class<T> clazz, IServiceFactory<T> factory);
	
	<T> T getService(IServiceContext cxt, IServiceReference<T> ref);
	
	void ungetService(IServiceContext cxt, IServiceReference<?> ref);
}