package org.mapleir.deob.interproc.cxtsenscg;

import org.mapleir.ir.code.expr.invoke.Invocation;
import org.objectweb.asm.tree.MethodNode;

public class VCS {
	
	public final MethodNode caller;
	public final Invocation invoke;
	public final String name, desc;
	public final int type;
	
	public VCS(MethodNode caller, Invocation invoke, String name, String desc, int type) {
		this.caller = caller;
		this.invoke = invoke;
		this.name = name;
		this.desc = desc;
		this.type = type;
	}
}
