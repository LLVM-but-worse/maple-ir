package org.mapleir.deob.interproc.geompa;

public class Pair<T, U> {

	protected T o1;
	protected U o2;
	
	public Pair() {
		o1 = null;
		o2 = null;
	}

	public Pair(T o1, U o2) {
		this.o1 = o1;
		this.o2 = o2;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((o1 == null) ? 0 : o1.hashCode());
		result = prime * result + ((o2 == null) ? 0 : o2.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		@SuppressWarnings("rawtypes")
		Pair other = (Pair) obj;
		if (o1 == null) {
			if (other.o1 != null)
				return false;
		} else if (!o1.equals(other.o1))
			return false;
		if (o2 == null) {
			if (other.o2 != null)
				return false;
		} else if (!o2.equals(other.o2))
			return false;
		return true;
	}

//	/**
//	 * Decide if this pair represents a method parameter.
//	 */
//	public boolean isParameter() {
//		if (o1 instanceof SootMethod && o2 instanceof Integer)
//			return true;
//		return false;
//	}
//
//	/**
//	 * Decide if this pair stores the THIS parameter for a method.
//	 */
//	public boolean isThisParameter() {
//		return (o1 instanceof SootMethod && o2.equals(PointsToAnalysis.THIS_NODE)) ? true : false;
//	}

	@Override
	public String toString() {
		return "Pair " + o1 + "," + o2;
	}

	public T getO1() {
		return o1;
	}

	public U getO2() {
		return o2;
	}

	public void setO1(T no1) {
		o1 = no1;
	}

	public void setO2(U no2) {
		o2 = no2;
	}

	public void setPair(T no1, U no2) {
		o1 = no1;
		o2 = no2;
	}
}