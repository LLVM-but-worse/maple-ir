package org.mapleir.stdlib.ir.gen.interference;

import org.mapleir.stdlib.collections.graph.FastGraphVertex;
import org.mapleir.stdlib.ir.locals.Local;

public class ColourableNode implements FastGraphVertex {

	private final Local local;
	private int colour;
	
	public ColourableNode(Local local, int colour) {
		this.local = local;
		this.colour = colour;
	}
	
	public Local getLocal() {
		return local;
	}
	
	public int getColour() {
		return colour;
	}
	
	@Override
	public String getId() {
		return local.toString() + " [paint=" + Integer.toString(colour) + "]";
	}
	
	@Override
	public String toString() {
		return getId();
	}
}