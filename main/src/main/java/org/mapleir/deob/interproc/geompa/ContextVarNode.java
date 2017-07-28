package org.mapleir.deob.interproc.geompa;

public class ContextVarNode extends LocalVarNode {
	private Context context;

	ContextVarNode(PAG pag, LocalVarNode base, Context context) {
		super(pag, base.getVariable(), base.getType(), base.getMethod());
		this.context = context;
		base.addContext(this, context);
	}

	@Override
	public Context context() {
		return context;
	}

	@Override
	public String toString() {
		return "ContextVarNode " + getNumber() + " " + variable + " " + method + " " + context;
	}
}