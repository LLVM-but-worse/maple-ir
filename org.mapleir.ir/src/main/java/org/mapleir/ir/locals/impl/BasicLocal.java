package org.mapleir.ir.locals.impl;

import java.util.concurrent.atomic.AtomicInteger;

import org.mapleir.ir.locals.Local;

public class BasicLocal extends Local {

	public BasicLocal(AtomicInteger base, int index) {
		this(base, index, false);
	}
	
	public BasicLocal(AtomicInteger base, int index, boolean stack) {
		super(base, index, stack);
	}
}