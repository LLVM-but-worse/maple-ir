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

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		CallGraphBlock that = (CallGraphBlock) o;

		return id == that.id;
	}

	@Override
	public int hashCode() {
		return id;
	}
}
