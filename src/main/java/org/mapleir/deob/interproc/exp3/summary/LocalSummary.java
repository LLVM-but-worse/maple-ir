package org.mapleir.deob.interproc.exp3.summary;

import java.util.HashMap;
import java.util.Map;

import org.mapleir.deob.interproc.exp3.summary.Value.ValueSet;
import org.mapleir.ir.locals.Local;
import org.mapleir.ir.locals.VersionedLocal;

public class LocalSummary {

	protected final Map<Local, ValueSet> locals;
	
	public LocalSummary(LocalSummary summary) {
		locals = new HashMap<>(summary.locals);
	}
	
	public LocalSummary() {
		locals = new HashMap<>();
	}
	
	public void setLocalValue(Local local, ValueSet value) {
		locals.put(local, value);
	}
	
	public ValueSet getLocalValue(Local local) {
		if(locals.containsKey(local)) {
			return locals.get(local);
		} else {
			throw new IllegalStateException(String.format("%s not defined", local));
		}
	}
	
	public LocalSummary copy() {
		return new LocalSummary(this);
	}
	
	public static class SSALocalSummary extends LocalSummary {
		public SSALocalSummary() {
			super();
		}
		
		public SSALocalSummary(LocalSummary summary) {
			super(summary);
		}
		
		@Override
		public ValueSet getLocalValue(Local local) {
			if(!(local instanceof VersionedLocal)) {
				throw new UnsupportedOperationException(String.format("Only versioned locals allowed: %s", local));
			}
			return super.getLocalValue(local);
		}
		
		@Override
		public void setLocalValue(Local local, ValueSet value) {
			if(!(local instanceof VersionedLocal)) {
				throw new UnsupportedOperationException(String.format("Only versioned locals allowed: %s", local));
			}
			if(locals.containsKey(local)) {
				throw new IllegalStateException(String.format("Cannot redefine %s (old:%d, new: %s)", local, locals.get(local), value));
			} else {
				super.setLocalValue(local, value);
			}
		}
		
		@Override
		public SSALocalSummary copy() {
			return new SSALocalSummary(this);
		}
	}
}