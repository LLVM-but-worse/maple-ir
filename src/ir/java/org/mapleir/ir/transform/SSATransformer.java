package org.mapleir.ir.transform;

import org.mapleir.ir.code.CodeBody;
import org.mapleir.ir.transform.ssa.SSALocalAccess;

public abstract class SSATransformer extends Transformer {

	protected final SSALocalAccess localAccess;

	public SSATransformer(CodeBody code, SSALocalAccess localAccess) {
		super(code);
		this.localAccess = localAccess;
	}
}