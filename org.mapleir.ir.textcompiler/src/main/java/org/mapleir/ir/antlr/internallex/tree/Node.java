package org.mapleir.ir.antlr.internallex.tree;

import java.util.List;

import org.mapleir.ir.antlr.internallex.Name;

public abstract class Node {

	public enum Tag {
		TOPLEVEL,
		SET_DIRECTIVE,
		IDENT,
		
	    FLOATLIT, 
	    DOUBLELIT, 
	    LONGLIT, 
	    INTLIT, 
	    CHARLIT, 
	    STRLIT, 
	    TYPELIT,
		
		CLASSDEF,
		METHODDEF,
		FIELDDEF,
		
		BLOCK
	}

	public Tag tag;
	public int pos;
	
	public Node(Tag tag, int pos) {
		this.tag = tag;
		this.pos = pos;
	}
	
	public static abstract class Expr extends Node {

		public Expr(Tag tag, int pos) {
			super(tag, pos);
		}
	}
	
	public static class Module extends Node {
		public Module(Tag tag, int pos) {
			super(tag, pos);
		}
	}
	
	public static class Statement extends Node {
		public Statement(Tag tag, int pos) {
			super(tag, pos);
		}
	}
	
	public static class Block extends Node {
		public List<Statement> statements;
		
		public Block(int pos, List<Statement> statements) {
			super(Tag.BLOCK, pos);
			this.statements = statements;
		}
	}
	
	public static class SetDirective extends Node {
		public Ident name;
		public Expr expr;
		
		public SetDirective(int pos, Ident name, Expr expr) {
			super(Tag.SET_DIRECTIVE, pos);
			this.name = name;
			this.expr = expr;
		}
	}
	
	public static class Ident extends Expr {
		public Name name;
		
		public Ident(int pos, Name name) {
			super(Tag.IDENT, pos);
			this.name = name;
		}
	}
	
	public static class Literal extends Node {
		public Object lit;
		
		public Literal(Tag tag, int pos, Object lit) {
			super(tag, pos);
			this.lit = lit;
		}
	}
}
