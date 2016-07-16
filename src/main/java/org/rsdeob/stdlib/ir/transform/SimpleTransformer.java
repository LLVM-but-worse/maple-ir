package org.rsdeob.stdlib.ir.transform;

import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.transform.impl.CodeAnalytics;

public abstract class SimpleTransformer extends Transformer {

	protected final CodeAnalytics analytics;
	
	public SimpleTransformer(CodeBody code, CodeAnalytics analytics) {
		super(code);
		this.analytics = analytics;
	}
}