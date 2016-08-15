package org.mapleir.ir.transform;

import org.mapleir.ir.code.CodeBody;

public abstract class Transformer {

	protected final CodeBody code;
	
	public Transformer(CodeBody code) {
		this.code = code;
	}
	
	public abstract int run();
}