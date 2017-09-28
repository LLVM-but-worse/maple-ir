package org.mapleir.serviceframework.api;

public interface IServiceQuery<T> {

	boolean accept(IServiceRegistry registry, Class<T> clazz, IServiceReference<T> ref);
}