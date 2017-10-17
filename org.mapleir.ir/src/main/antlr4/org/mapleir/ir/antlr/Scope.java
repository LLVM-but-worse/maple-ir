package org.mapleir.ir.antlr;

import java.util.HashMap;
import java.util.Map;

public class Scope {

	private final Map<String, Object> properties = new HashMap<>();
	
	private Scope parent;
	
	public Scope() {
		// global scope
		this(null);
	}
	
	public Scope(Scope parent) {
		this.parent = parent;
	}
	
	public void setProperty(String key, Object v) {
		properties.put(key, v);
	}
	
	public void update() {
		
	}
}