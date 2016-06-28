package org.rsdeob.stdlib.ir.api;

public interface ICodeListener<N> {

	void updated(N n);
	
	void replaced(N old, N  n);
	
	void added(N n);
	
	void removed(N n);

	void commit();
}