package org.rsdeob.stdlib.ir.api;

public interface ICodeListener<N> {

	void update(N n);

	void replaced(N old, N  n);
	
	void preRemove(N n);
	
	void postRemove(N n);
	
	void insert(N p, N s, N n);

	void commit();
}