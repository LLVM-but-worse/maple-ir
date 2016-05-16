package org.rsdeob.stdlib.asm;

import org.objectweb.asm.tree.analysis.Value;

public class TupleValue<T1 extends Value, T2 extends Value> implements Value {

	private final T1 t1;
	private final T2 t2;
	
	public TupleValue(T1 t1, T2 t2) {
		this.t1 = t1;
		this.t2 = t2;
	}

	public T1 getT1() {
		return t1;
	}

	public T2 getT2() {
		return t2;
	}

	@Override
	public int getSize() {
		int s1 = t1.getSize();
		int s2 = t2.getSize();
		if(s1 != s2) {
			throw new UnsupportedOperationException(String.format("s1=%d, s2=%d", s1, s2));
		}
		return s1;
	}
	
	@Override
	public boolean equals(Object o) {
		if(o == null) {
			return false;
		}
		
		if(o.getClass().equals(t1.getClass())) {
			return t1.equals(o);
		} else if(o.getClass().equals(t2.getClass())) {
			return t2.equals(o);
		} else if(o.getClass().equals(getClass())) {
			TupleValue<?, ?> tuple = (TupleValue<?, ?>) o;
			return tuple.t1.equals(t1) && tuple.t2.equals(t2);
		} else {
			return false;
		}
	}
}