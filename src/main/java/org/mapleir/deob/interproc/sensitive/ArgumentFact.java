package org.mapleir.deob.interproc.sensitive;

import java.util.Arrays;

public abstract class ArgumentFact {

	public static class AnyValueFact extends ArgumentFact {
		public static final AnyValueFact INSTANCE = new AnyValueFact();
		
		private AnyValueFact() {
		}
	}
	
	public static class ConstantValueFact extends ArgumentFact {
		private final Object value;

		public ConstantValueFact(Object value) {
			this.value = value;
		}

		@Override
		public int hashCode() {
			int result = 1;
			result = 31 * result + ((value == null) ? 0 : value.hashCode());
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
			ConstantValueFact other = (ConstantValueFact) obj;
			if (value == null) {
				if (other.value != null)
					return false;
			} else if (!value.equals(other.value))
				return false;
			return true;
		}
	}
	
	public static class PhiValueFact extends ArgumentFact {
		private final ArgumentFact[] phiArgs;

		public PhiValueFact(ArgumentFact[] phiArgs) {
			this.phiArgs = phiArgs;
		}

		@Override
		public int hashCode() {
			int result = 1;
			result = 37 * result + Arrays.hashCode(phiArgs);
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
			PhiValueFact other = (PhiValueFact) obj;
			if (!Arrays.equals(phiArgs, other.phiArgs))
				return false;
			return true;
		}
	}
}