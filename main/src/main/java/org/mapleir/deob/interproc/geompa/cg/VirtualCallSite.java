package org.mapleir.deob.interproc.geompa.cg;

import org.mapleir.ir.code.Stmt;
import org.mapleir.ir.code.expr.invoke.Invocation;
import org.objectweb.asm.tree.MethodNode;

public class VirtualCallSite {
	public final Invocation iie;
	public final Stmt stmt;
	public final MethodNode container;
	
	public final String owner;
	public final String name;
	public final String desc;
	
	public final Kind kind;

	public VirtualCallSite(Stmt stmt, MethodNode container, Invocation iie, String owner, String name, String desc, Kind kind) {
		this.stmt = stmt;
		this.container = container;
		this.iie = iie;
		this.owner = owner;
		this.name = name;
		this.desc = desc;
		this.kind = kind;
	}
}