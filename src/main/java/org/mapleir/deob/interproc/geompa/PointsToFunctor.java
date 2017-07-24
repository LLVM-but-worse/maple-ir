package org.mapleir.deob.interproc.geompa;

public interface PointsToFunctor<R> {
	void apply(PointsToNode n);
	
	R getResult();
	
	public static abstract class BooleanPointsToFunctor implements PointsToFunctor<Boolean> {
		protected boolean res;
		
		public BooleanPointsToFunctor() {
		}
		
		public BooleanPointsToFunctor(boolean initialRes) {
			res = initialRes;
		}
		
		@Override
		public abstract void apply(PointsToNode n);
		
		@Override
		public Boolean getResult() {
			return res;
		}
	}
}