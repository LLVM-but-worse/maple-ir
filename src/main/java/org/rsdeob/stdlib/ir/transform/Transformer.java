package org.rsdeob.stdlib.ir.transform;

import org.rsdeob.stdlib.ir.CodeBody;

public abstract class Transformer {

	protected final CodeBody code;
	
	public Transformer(CodeBody code) {
		this.code = code;
	}
	
	public abstract int run();
}