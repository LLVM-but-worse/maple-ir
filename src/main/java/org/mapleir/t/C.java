package org.mapleir.t;

public class C extends A {
	@Override
	public I1Impl m(int i) {
		return new I1Impl(i);
	}
}