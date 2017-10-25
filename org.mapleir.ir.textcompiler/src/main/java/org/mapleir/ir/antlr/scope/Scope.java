package org.mapleir.ir.antlr.scope;

import java.util.ArrayList;
import java.util.List;

import org.mapleir.ir.antlr.CompilationDriver;
import org.mapleir.ir.antlr.directive.DirectiveToken;

public abstract class Scope {

	protected final CompilationDriver driver;
	private final List<DirectiveToken> properties;
	
	protected Scope parent;

	// global scope
	public Scope(CompilationDriver driver) {
		this(driver, null);
	}
	
	public Scope(CompilationDriver driver, Scope parent) {
		this.driver = driver;
		this.parent = parent;
		properties = new ArrayList<>();
	}
	
	public void addDirective(DirectiveToken directive) {
		properties.add(directive);
	}
	
	public Scope getParent() {
		return parent;
	}
}