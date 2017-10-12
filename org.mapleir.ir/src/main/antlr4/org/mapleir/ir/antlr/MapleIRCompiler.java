package org.mapleir.ir.antlr;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import org.antlr.v4.runtime.Token;
import org.apache.log4j.Logger;
import org.mapleir.ir.antlr.mapleirParser.CompilationUnitContext;
import org.mapleir.ir.antlr.mapleirParser.SetCommandValueContext;

public class MapleIRCompiler extends mapleirBaseListener {

	private static final Logger LOGGER = Logger.getLogger(MapleIRCompiler.class);
	private Deque<Scope> scopes;
	private CompilationUnitContext compilationUnit;
	private Deque<CompilationException> exceptions;

	public MapleIRCompiler() {
		scopes = new LinkedList<>();
	}

	@Override
	public void enterCompilationUnit(mapleirParser.CompilationUnitContext ctx) {
		if (compilationUnit != null) {
			throw new IllegalStateException("Can only process one compilation unit per file");
		}

		compilationUnit = ctx;
		scopes.push(new BasicScope());
	}

	@Override
	public void exitCompilationUnit(mapleirParser.CompilationUnitContext ctx) {
		compilationUnit = null;
	}

	@Override
	public void exitSetDirective(mapleirParser.SetDirectiveContext ctx) {
		Scope currentScope = scopes.peek();

		if (currentScope == null) {
			Token t = ctx.getStart();
			throw new CompilationException(t.getLine(), -1, "Tried to use set directive outside of active scopes");
		}

		for (SetCommandValueContext v : ctx.setCommandValueList().setCommandValue()) {
			try {
				System.out.println(decodeValue(v));
			} catch (ParseException e) {
				Token t = v.getStart();
				throw new CompilationException(t.getLine(), t.getCharPositionInLine(),
						String.format("Malformed input: %s", v.getText()), e);
			}
		}
	}

	private Object decodeValue(SetCommandValueContext v) throws ParseException {
		if (v.LITERAL() != null) {
			// TODO: fix proper integer types
			return decodeLiteral(v.LITERAL().getText());
//			v.LITERAL().
		} else {
			return v.Identifier().getText();
		}
	}

	private Object decodeLiteral(String t) throws ParseException {
		if(t.equals("null")) {
			return null;
		} else if(t.equals("true") || t.equals("false")) {
			return Boolean.parseBoolean(t);
		} else if(t.startsWith("\"")) {
			return t.substring(1, t.length()-1);
		} else if(t.startsWith("'")) {
			String str = t.substring(1, t.length()-1);
			if(str.length() == 1) {
				return str.charAt(0);
			} else {
				throw new RuntimeException(); // shouldn't happen (lexer rules)
			}
		} else {
			if(t.startsWith("\\u")) {
				
			}
			System.out.println(t);
			return NumberFormat.getNumberInstance().parse(t);
		}
	}
}
