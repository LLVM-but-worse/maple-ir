package org.mapleir.t;

public class I1Impl implements I1 {
	final int i;
	public I1Impl(int i) {
		this.i = i;
	}

	@Override
	public
	String toString() {
		return "hi " + i;
	}
}