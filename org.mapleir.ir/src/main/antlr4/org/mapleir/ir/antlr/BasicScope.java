package org.mapleir.ir.antlr;

import java.util.HashMap;
import java.util.Map;

public class BasicScope implements Scope {

	private final Map<String, Object> properties = new HashMap<>();
	
	@Override
	public void setProperty(String key, Object v) {
		properties.put(key, v);
	}
}