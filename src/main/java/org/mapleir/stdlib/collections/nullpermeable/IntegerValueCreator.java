package org.mapleir.stdlib.collections.nullpermeable;

import java.util.concurrent.atomic.AtomicInteger;

public class IntegerValueCreator implements ValueCreator<AtomicInteger> {

	@Override
	public AtomicInteger create() {
		return new AtomicInteger();
	}
}