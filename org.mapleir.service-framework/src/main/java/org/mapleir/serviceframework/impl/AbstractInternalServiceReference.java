package org.mapleir.serviceframework.impl;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractInternalServiceReference<T> implements IInternalServiceReference<T> {

	private final AtomicInteger monitors = new AtomicInteger();
	
	@Override
	public abstract T get();
	
	@Override
	public void lock() {
		monitors.incrementAndGet();
	}

	@Override
	public void unlock() {
		monitors.decrementAndGet();
	}

	@Override
	public boolean locked() {
		return monitors.get() > 0;
	}
}