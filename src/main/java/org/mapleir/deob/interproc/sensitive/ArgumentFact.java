package org.mapleir.deob.interproc.sensitive;

import org.mapleir.stdlib.collections.TaintableSet;
import org.mapleir.stdlib.collections.taint.ITaintable;

public interface ArgumentFact extends ITaintable {
	
	public static class AnyValueFact implements ArgumentFact {
		public static final AnyValueFact INSTANCE = new AnyValueFact();
		
		private AnyValueFact() {
		}
		
		@Override
		public boolean isTainted() {
			return true;
		}
		
		@Override
		public boolean union(ITaintable t) {
			return true;
		}
	}

	public static class ConstantValueFact implements ArgumentFact {
		private final Object value;

		public ConstantValueFact(Object value) {
			this.value = value;
		}
		
		@Override
		public int hashCode() {
			return value != null ? value.hashCode() : 0;
		}
		
		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			
			ConstantValueFact that = (ConstantValueFact) o;
			
			return value != null ? value.equals(that.value) : that.value == null;
		}
		
		@Override
		public boolean isTainted() {
			return false;
		}
		
		@Override
		public boolean union(ITaintable t) {
			throw new IllegalStateException();
		}
	}

	public static class PhiValueFact implements ArgumentFact {
		private final TaintableSet<ArgumentFact> phiArgs;

		public PhiValueFact(TaintableSet<ArgumentFact> phiArgs) {
			this.phiArgs = phiArgs;
		}
		
		@Override
		public boolean isTainted() {
			return phiArgs.isTainted();
		}
		
		@Override
		public boolean union(ITaintable t) {
			return phiArgs.union(t);
		}
		
		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			
			PhiValueFact that = (PhiValueFact) o;
			
			return phiArgs.equals(that.phiArgs);
		}
		
		@Override
		public int hashCode() {
			return phiArgs.hashCode();
		}
	}
}