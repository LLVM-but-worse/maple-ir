package org.mapleir.ir.antlr.scope;

import java.util.ArrayList;
import java.util.List;

import org.mapleir.ir.antlr.directive.DirectiveToken;

public abstract class Scope {

	private final List<DirectiveToken> properties;
	
	protected Scope parent;

	// global scope
	public Scope() {
		this(null);
	}
	
	public Scope(Scope parent) {
		this.parent = parent;
		properties = new ArrayList<>();
	}

	public List<DirectiveToken> getProperties() {
		return properties;
	}
	
	public Scope getParent() {
		return parent;
	}
}