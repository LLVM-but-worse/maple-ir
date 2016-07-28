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
		return local.toString();
	}
	
	@Override
	public String toString() {
		return local.toString() + " [paint=" + Integer.toString(colour) + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + colour;
		result = prime * result + ((local == null) ? 0 : local.toString().hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ColourableNode other = (ColourableNode) obj;
		if (colour != other.colour)
			return false;
		if (local == null) {
			if (other.local != null)
				return false;
		} else if (!local.equals(other.local))
			return false;
		return true;
	}
}