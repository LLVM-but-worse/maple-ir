package org.rsdeob.stdlib.ir.transform.impl;

import org.rsdeob.stdlib.ir.CodeBody;

public abstract class Transformer {

	protected final CodeBody code;
	protected final CodeAnalytics analytics;
	
	public Transformer(CodeBody code, CodeAnalytics analytics) {
		this.code = code;
		this.analytics = analytics;
	}
	
	public abstract int run();
}