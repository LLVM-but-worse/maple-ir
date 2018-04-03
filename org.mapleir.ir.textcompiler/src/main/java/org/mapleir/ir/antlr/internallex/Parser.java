package org.mapleir.ir.antlr.internallex;

import java.util.ArrayList;
import java.util.List;

import org.mapleir.ir.antlr.internallex.tree.Node;

public class Parser {
	
	private Lexer lexer;
	private Name.Table names;
	private int mode;
	private int lastmode;
	
	public Parser(Lexer lexer, Name.Table names) {
		this.lexer = lexer;
		this.names = names;
	}
	
	public void compilationUnit() {
		List<Node.SetDirective> directives = new ArrayList<>();
		Token tk;
		while((tk = lexer.token()) != Token.EOF) {
			if(tk == Token.SET) {
				directives.add(setDirective());
			} else {
				
			}
		}
	}
	
	List<Node.Statement> blockStatements() {
		List<Node.Statement> stmts = new ArrayList<>();
		while(true) {
			int pos = lexer.tokenPos();
			switch(lexer.token()) {
				case RBRACE: case CASE: case DEFAULT: case EOF:
					return stmts;
				case LBRACE: case IF: case GOTO: case CONSUME:
				case RETURN: case THROW: case MON_ENTER: case MON_EXIT:
					stmts.add(statement());
					break;
				default:
//					lexer.name()
					break;
			}
		}
	}
	
	Node.Statement statement() {
		return null;
	}
	
	Node.Block block(int pos) {
		accept(Token.LBRACE);
	}
	
	Node.SetDirective setDirective() {
		int pos = lexer.tokenPos();
		lexer.nextToken();
	}
	
	Node.Expr term(int newmode) {
		int prevmode = mode;
        mode = newmode;
        Node.Expr t = term();
        lastmode = mode;
        mode = prevmode;
        return t;
	}
	
	Node.Expr term() {
		return null;
	}
	
	Name ident() {
		Token tk = lexer.token();
		if(tk == Token.IDENTIFIER ||
				tk.toString().matches("\\\\b[_a-zA-Z][_a-zA-Z0-9]*\\\\b")) {
			Name name = lexer.name();
			lexer.nextToken();
			return name;
		} else {
			accept(Token.IDENTIFIER);
			return names.fromString("<error>");
		}
	}
	
	public void accept(Token t) {
		if(lexer.token() == t) {
			lexer.nextToken();
		} else {
			System.out.printf("expected %s", t);
		}
	}
}
