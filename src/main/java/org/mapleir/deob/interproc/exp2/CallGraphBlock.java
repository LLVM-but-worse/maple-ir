package org.mapleir.deob.interproc.exp2;

import org.mapleir.stdlib.collections.graph.FastGraphVertex;

public abstract class CallGraphBlock implements FastGraphVertex {

	private final int id;
	
	public CallGraphBlock(int id) {
		this.id = id;
	}
	
	@Override
	public String getId() {
		return String.valueOf(id);
	}

	@Override
	public int getNumericId() {
		return id;
	}
}