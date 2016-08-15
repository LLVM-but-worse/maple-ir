package org.mapleir.stdlib.ir.transform;

import org.mapleir.stdlib.ir.CodeBody;
import org.mapleir.stdlib.ir.transform.impl.CodeAnalytics;

public abstract class SimpleTransformer extends Transformer {

	protected final CodeAnalytics analytics;
	
	public SimpleTransformer(CodeBody code, CodeAnalytics analytics) {
		super(code);
		this.analytics = analytics;
	}
}