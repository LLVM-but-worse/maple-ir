package org.mapleir.ir.locals;

import java.util.concurrent.atomic.AtomicInteger;

public class BasicLocal extends Local {

	public BasicLocal(AtomicInteger base, int index) {
		this(base, index, false);
	}
	
	public BasicLocal(AtomicInteger base, int index, boolean stack) {
		super(base, index, stack);
	}
}