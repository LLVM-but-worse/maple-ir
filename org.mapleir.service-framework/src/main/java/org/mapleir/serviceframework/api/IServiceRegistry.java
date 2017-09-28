package org.mapleir.serviceframework.api;

import java.util.Collection;

public interface IServiceRegistry {

	<T> IServiceReference<T> getServiceReference(IServiceContext cxt, Class<T> clazz);
	
	<T> Collection<IServiceReference<T>> getServiceReferences(IServiceContext cxt, Class<T> clazz, IServiceQuery<T> query);
	
	<T> void registerService(IServiceContext cxt, Class<T> clazz, T obj);
	
	<T> void registerServiceFactory(IServiceContext cxt, Class<T> clazz, IServiceFactory<T> factory);
	
	<T> T getService(IServiceReference<T> ref);
	
	void ungetService(IServiceReference<?> ref);
}