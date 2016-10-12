package org.mapleir.ir.locals;

import java.util.concurrent.atomic.AtomicInteger;

public class VersionedLocal extends Local {

	private int subscript;
	
	public VersionedLocal(AtomicInteger base, int index, int subscript) {
		super(base, index);
		this.subscript = subscript;
	}
	
	public VersionedLocal(AtomicInteger base, int index, int subscript, boolean stack) {
		super(base, index, stack);
		this.subscript = subscript;
	}
	
	public int getSubscript() {
		return subscript;
	}
	
	@Override
	public int getCodeIndex() {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public String toString() {
		return super.toString() + "_" + subscript;
	}

	@Override
	public int hashCode() {
//		return toString().hashCode();
		final int prime = 257;
		int result = super.hashCode();
		result = prime * result + subscript;
		return result;
	}

	@Override
	public int compareTo(Local o) {
		if(!(o instanceof VersionedLocal)) {
			throw new UnsupportedOperationException(this + " vs " + o.toString());
		}

		int comp = super.compareTo(o);
		
		VersionedLocal v = (VersionedLocal) o;
		if(subscript == 0 && v.subscript != 0) {
			return -1;
		} else if(subscript != 0 && v.subscript == 0) {
			return 1;
		}
		
		if(comp == 0) {
			comp = Integer.compare(subscript, v.subscript);
		}
		return comp;
	}
}