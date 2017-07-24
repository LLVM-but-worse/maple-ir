package org.mapleir.deob.interproc.exp3.summary;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.mapleir.ir.code.Expr;

public interface Value {
	Value UNKNOWN = new Value() {
		
		@Override
		public boolean isTop() {
			return true;
		}
		
		@Override
		public ValueSet merge(Value other) {
			return TOPSET;
		}

		@Override
		public Value copy() {
			return this;
		}
		
		@Override
		public String toString() {
			return "unknown";
		}
	};

	ValueSet TOPSET = new ValueSet() {
		{
			add(UNKNOWN);
		}
		
		@Override
		public boolean isTop() {
			return true;
		}
		
		@Override
		public ValueSet merge(Value v) {
			/* always TOP */
			return this;
		}
		
		@Override
		public ValueSet copy() {
			return this;
		}
		
		@Override
		public String toString() {
			return "topset";
		}
	};
	
	Value SELF_OBJECT = new Value() {
		@Override
		public Value copy() {
			return this;
		}
		
		@Override
		public String toString() {
			return "this";
		}
	};
	
	public static class ConstExprValue implements Value {
		private final Expr expr;
		
		public ConstExprValue(Expr expr) {
			this.expr = expr;
		}
		
		public Expr getExpression() {
			return expr;
		}

		@Override
		public ConstExprValue copy() {
			return this;
		}
		
		@Override
		public String toString() {
			return expr.toString();
		}
	}
	
	public static class ValueSet implements Value, Iterable<Value> {
		public static ValueSet make(Value... values) {
			ValueSet res = new ValueSet();
			for(Value v : values) {
				res = res.merge(v);
			}
			return res;
		}
		
		private final Set<Value> values;
		
		public ValueSet() {
			values = new HashSet<>();
		}
		
		protected void add(Value v) {
			values.add(v);
		}
		
		protected void remove(Value v) {
			values.remove(v);
		}
		
		@Override
		public ValueSet merge(Value v) {
			if(v.isTop()) {
				return TOPSET;
			} else {
				assertUntop();
				add(v);
				return this;
			}
		}
		
		private void assertUntop() {
			for(Value v : values) {
				if(v.isTop()) {
					throw new IllegalStateException(this.toString());
				}
			}
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("[");

			Iterator<Value> it = values.iterator();
			while(it.hasNext()) {
				Value v = it.next();
				sb.append(v.toString());
				
				if(it.hasNext()) {
					sb.append(", ");
				}
			}
			
			sb.append("]");
			return sb.toString();
		}

		@Override
		public Iterator<Value> iterator() {
			return values.iterator();
		}

		@Override
		public ValueSet copy() {
			ValueSet set = new ValueSet();
			set.values.addAll(values);
			return set;
		}
	}
	
	default boolean isTop() {
		return false;
	}
	
	default ValueSet merge(Value other) {
		return other.merge(this);
	}
	
	Value copy();
	
//	Value UNDEFINED = new Value() {
//	/* defined here */
//	@Override
//	public Value merge(Value other) {
//		return other;
//	}
//	
//	@Override
//	public String toString() {
//		return "undefined";
//	}
//	
//	@Override
//	public Value copy() {
//		return this;
//	}
//};
}