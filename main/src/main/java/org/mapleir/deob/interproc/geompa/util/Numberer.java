package org.mapleir.deob.interproc.geompa.util;

public interface Numberer<E> {
	void add(E o);

	long get(E o);

	E get(long number);

	int size();
}