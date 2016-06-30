package org.rsdeob.stdlib.ir.api;

public interface ICodeListener<N> {

	void update(N n);

	void replaced(N old, N  n);
	
	void remove(N n);
	
	void insert(N p, N s, N n);

	void commit();
}