package org.rsdeob.stdlib.asm;

public interface TupleCreationFactory<V, T1, T2> {
	
	V create(T1 t1, T2 t2);
}