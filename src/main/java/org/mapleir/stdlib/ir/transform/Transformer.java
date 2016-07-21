package org.mapleir.stdlib.ir.transform;

import org.mapleir.stdlib.ir.CodeBody;

public abstract class Transformer {

	protected final CodeBody code;
	
	public Transformer(CodeBody code) {
		this.code = code;
	}
	
	public abstract int run();
}