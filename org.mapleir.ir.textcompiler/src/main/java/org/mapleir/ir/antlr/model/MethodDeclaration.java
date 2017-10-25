package org.mapleir.ir.antlr.model;

import org.mapleir.ir.cfg.ControlFlowGraph;

public class MethodDeclaration extends ClassMemberDeclaration {

	private ControlFlowGraph cfg;
	
	public MethodDeclaration() {
	}
	public ControlFlowGraph getCfg() {
		return cfg;
	}

	public void setCfg(ControlFlowGraph cfg) {
		this.cfg = cfg;
	}
}