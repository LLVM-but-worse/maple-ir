package org.mapleir.serviceframework.api;

public interface IServiceReferenceHandler {

	<T> T loadService(IServiceReference<T> ref);

	void unloadService(IServiceReference<?> ref);
}