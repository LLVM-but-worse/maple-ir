package org.mapleir.deob.interproc.builder;

import org.mapleir.stdlib.collections.graph.FastGraphVertex;

public class CallGraphNode implements FastGraphVertex {

	private final int id;
	
	public CallGraphNode(int id) {
		this.id = id;
	}
	
	@Override
	public String getId() {
		return Integer.toString(id);
	}

	@Override
	public int getNumericId() {
		return id;
	}
}