package org.mapleir.ir.antlr.internallex.tree;

public class TreeMaker {

	private int pos;
	
	public TreeMaker() {
		pos = -1;
	}
	
	public TreeMaker at(int pos) {
		this.pos = pos;
		return this;
	}
}
