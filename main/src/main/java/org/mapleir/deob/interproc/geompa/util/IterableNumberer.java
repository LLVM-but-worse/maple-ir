package org.mapleir.deob.interproc.geompa.util;

import java.util.Iterator;

public interface IterableNumberer<E> extends Numberer<E>, Iterable<E> {
	
    @Override
	Iterator<E> iterator(); 
}