package org.rsdeob.stdlib.ir.transform;

import org.rsdeob.stdlib.ir.CodeBody;
import org.rsdeob.stdlib.ir.transform.ssa.SSALocalAccess;

public abstract class SSATransformer extends Transformer {

	protected final SSALocalAccess localAccess;

	public SSATransformer(CodeBody code, SSALocalAccess localAccess) {
		super(code);
		this.localAccess = localAccess;
	}
}