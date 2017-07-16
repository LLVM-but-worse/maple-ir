package org.mapleir.deob.interproc.exp2;

import org.mapleir.ir.cfg.BasicBlock;

public class ConcreteCallGraphBlock extends CallGraphBlock {
	public final BasicBlock block;
	
	public ConcreteCallGraphBlock(BasicBlock block, int id) {
		super(id);
		this.block = block;
	}
	
	@Override
	public String toString() {
		return block.getGraph().getMethod() + "::" + block.toString();
	}
}