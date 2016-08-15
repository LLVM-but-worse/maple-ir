package org.mapleir.stdlib.ir.transform;

import org.mapleir.stdlib.ir.CodeBody;
import org.mapleir.stdlib.ir.transform.ssa.SSALocalAccess;

public abstract class SSATransformer extends Transformer {

	protected final SSALocalAccess localAccess;

	public SSATransformer(CodeBody code, SSALocalAccess localAccess) {
		super(code);
		this.localAccess = localAccess;
	}
}