package org.mapleir.ir.transform;

import org.mapleir.ir.code.CodeBody;
import org.mapleir.ir.analysis.dataflow.impl.CodeAnalytics;

public abstract class SimpleTransformer extends Transformer {

	protected final CodeAnalytics analytics;
	
	public SimpleTransformer(CodeBody code, CodeAnalytics analytics) {
		super(code);
		this.analytics = analytics;
	}
}