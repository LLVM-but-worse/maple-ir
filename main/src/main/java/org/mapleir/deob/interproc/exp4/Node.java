package org.mapleir.deob.interproc.exp4;

import org.mapleir.stdlib.collections.graph.FastGraphVertex;

public class Node implements FastGraphVertex {
	private final int id;
	
	public Node(int id) {
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
	
	@Override
	public final int hashCode() {
		return id;
	}
	
	@Override
	public boolean equals(Object o) {
		if(!(o instanceof Node)) {
			return false;
		}
		
		Node n = (Node) o;
		return this == n || n.id == id;
	}
}