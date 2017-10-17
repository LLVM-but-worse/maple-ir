package org.mapleir.ir.antlr;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import org.antlr.v4.runtime.Token;
import org.apache.log4j.Logger;
import org.mapleir.ir.antlr.mapleirParser.ClassDeclarationContext;
import org.mapleir.ir.antlr.mapleirParser.CompilationUnitContext;
import org.mapleir.ir.antlr.mapleirParser.JclassContext;
import org.mapleir.ir.antlr.mapleirParser.SetCommandValueContext;
import org.mapleir.ir.antlr.mapleirParser.SetDirectiveContext;

public class CompilationDriver extends mapleirBaseListener {

	private static final boolean BAIL_FAST = false;
	
	private static final Logger LOGGER = Logger.getLogger(CompilationDriver.class);
	private Deque<Scope> scopes;
	public CompilationUnitContext unit;
	private Deque<CompilationException> exceptions;
	
	private Token token;
	
	public CompilationDriver() {
		scopes = new LinkedList<>();
		exceptions = new LinkedList<>();
	}

	public void process(mapleirParser parser) {
		parser.addParseListener(this);
		unit = parser.compilationUnit();
		
		if (unit == null) {
			throw new CompilationException(0, 0, "No compilation unit to process");
		}

		/* push our global scope and process global directives */
		scopes.push(new Scope());
		
		processSetDirectives(unit.setDirective());
		processClassDecl(unit.classDeclaration());
		
		parser.removeParseListener(this);
		
		if(!exceptions.isEmpty()) {
			LOGGER.error("Compilation errors occured while processing file");
			
			for(CompilationException e : exceptions) {
				LOGGER.error("  * " + e.toString());
			}
		}
	}
	
	private void processClassDecl(ClassDeclarationContext cdecl) {
		checkClassName(cdecl.jclass());
		processSetDirectives(cdecl.setDirective());
	}
	
	private void processSetDirectives(List<SetDirectiveContext> directives) {
		if(directives != null && !directives.isEmpty()) {
			for(SetDirectiveContext sdctx : directives) {
				processSetDirective(sdctx);
			}
		}
	}

	private void processSetDirective(SetDirectiveContext ctx) {
		Scope currentScope = scopes.peek();

		if (currentScope == null) {
			Token t = ctx.getStart();
			error(t.getLine(), -1, "Tried to use set directive outside of an active scope");
		}

		List<SetCommandValueContext> commands = ctx.setCommandValueList().setCommandValue();
		List<Object> values = new ArrayList<>();
		for (SetCommandValueContext v : commands) {
			try {
				Object o = decodeValue(v);
				values.add(o);
			} catch (ParseException e) {
				Token t = v.getStart();
				error(t.getLine(), t.getCharPositionInLine() + e.getErrorOffset() + 1,
						String.format("Malformed input: %s", v.getText()), e);
			}
		}
		
		if(values.size() == 1) {
			currentScope.setProperty(ctx.Identifier().getText(), values.iterator().next());
		} else {
			currentScope.setProperty(ctx.Identifier().getText(), values);
		}
	}
	
	private void error(int line, int col, String msg, Exception t) {
		CompilationException e = new CompilationException(line, col, msg, t);
		
		if(BAIL_FAST) {
			throw e;
		} else {
			synchronized (exceptions) {
				exceptions.add(e);	
			}
		}
	}
	
	private void error(int line, int col, String msg) {
		error(line, col, msg, null);
	}
	
	private void error(String msg) {
		error(msg, 0);
	}
	
	private void error(String msg, int colOff) {
		if(token == null) {
			throw new IllegalStateException("internal error on no token");
		} else {
			error(token.getLine(), token.getCharPositionInLine() + colOff, msg);
		}
	}
	
	private void checkClassNameChar(char ch, char prevCh, int i) {
		if(ch == '.') {
			error("'.' instead of '/'", i+1);
		} else if(ch == '/') {
			if(prevCh == '/' || prevCh == '.') {
				error("no package name inbetween separators", i+1);
			}
		}
	}
	
	private void checkClassName(JclassContext jclass) {
		token = jclass.getStart();
		
		String input = jclass.getText();
		
		if(input == null || input.isEmpty()) {
			error("missing identifier");
		}
		
		char[] chars = input.toCharArray();
		char prevCh = 0;
		int i = 0;
		
		char ch = chars[i++];
		checkClassNameChar(ch, prevCh, 0);
		/* input starts with / or . */
		if(ch == '/' || ch == '.') {
			error("leading '/'", 1);
		}
		
		prevCh = ch;
		
		for(;i < chars.length; i++, prevCh = ch) {
			checkClassNameChar(ch = chars[i], prevCh, i);
		}
		
		if(ch == '/' || ch == '.') {
			/* input ends with / */
			error("no class name (only packages declared)", input.length()/*-1+1*/);
		}
	}

	private Object decodeValue(SetCommandValueContext v) throws ParseException {
		if (v.LITERAL() != null) {
			return decodeLiteral(v.LITERAL().getText());
		} else {
			return v.Identifier().getText();
		}
	}

	private Object decodeLiteral(String t) throws ParseException {
		if(t.equals("null")) {
			return null;
		} else if(t.equals("true") || t.equals("false")) {
			return Boolean.parseBoolean(t);
		} else {
			LiteralLexer lexer = new LiteralLexer(t.toCharArray());
			
			try {
				lexer.process();
			} catch(IllegalStateException e) {
				throw new ParseException(e.getMessage(), lexer.bp);
			}
			
			switch(lexer.tk) {
				case INTLIT: {
					try {
						return LiteralLexer.asInt(lexer.asString(), lexer.nradix);
					} catch (NumberFormatException e) {
						throw new ParseException(e.getMessage(), lexer.bp);
					}
				}
				case LONGLIT: {
					try {
						return LiteralLexer.asLong(lexer.asString(), lexer.nradix);
					} catch (NumberFormatException e) {
						throw new ParseException(e.getMessage(), lexer.bp);
					}
				}
				case FLOATLIT: {
					String proper = lexer.nradix == 16 ? ("0x" + lexer.asString()) : lexer.asString();
					
					Float n;
					
					try {
						n = Float.valueOf(proper);
					} catch(NumberFormatException ex) {
						 // should throw before we get to here
						n = Float.NaN;
					}
					
					if(n.floatValue() == 0.0f && !isZero(proper)) {
						throw new ParseException("fp number too small: " + proper, lexer.bp);
					} else if(n.floatValue() == Float.POSITIVE_INFINITY) {
						throw new ParseException("fp number too large: " + proper, lexer.bp);
					}
					
					return n;
				}
				case DOUBLELIT: {
					String proper = lexer.nradix == 16 ? ("0x" + lexer.asString()) : lexer.asString();
					
					Double n;
		            try {
		                n = Double.valueOf(proper);
		            } catch (NumberFormatException ex) {
		            	 // should throw before we get to here
		                n = Double.NaN;
		            }
		            
		            if(n.doubleValue() == 0.0d && !isZero(proper)) {
						throw new ParseException("fp number too small: " + proper, lexer.bp);
		            } else if(n.floatValue() == Float.POSITIVE_INFINITY) {
						throw new ParseException("fp number too large: " + proper, lexer.bp);
					}
		            
		            return n;
				}
				case CHARLIT:
					return lexer.asString().charAt(0);
				case STRLIT:
					return lexer.asString();
				default:
					throw new UnsupportedOperationException("Unknown token type: " + lexer.tk);
			}
		}
	}
	
	private boolean isZero(String s) {
		char[] cs = s.toCharArray();
		int base = ((cs.length > 1 && Character.toLowerCase(cs[1]) == 'x') ? 16 : 10);
		int i = ((base == 16) ? 2 : 0);
		while (i < cs.length && (cs[i] == '0' || cs[i] == '.')) {
			i++;
		}
		return !(i < cs.length && (Character.digit(cs[i], base) > 0));
	}
	
	public static class LiteralLexer {
		enum TokenKind {
			FLOATLIT, DOUBLELIT, LONGLIT, INTLIT, CHARLIT(false), STRLIT(false);
			
			private final boolean numeric;
			
			private TokenKind() {
				this(true);
			}
			
			private TokenKind(boolean numeric) {
				this.numeric = numeric;
			}
			
			public boolean isNumeric() {
				return numeric;
			}
		}
		final char[] buf;
		int bp;
		int unicodeConversionBp;
		char ch;
		TokenKind tk;
		int nradix;
		
		char[] sbuf;
		int sp;
		
		public LiteralLexer(char[] buf) {
			this.buf = Arrays.copyOf(buf, buf.length+1);
			this.unicodeConversionBp = this.bp = -1;
			this.ch = 0;
			
			buf[buf.length-1] = 0x10; // EOI
			
			sbuf = new char[128];
			sp = 0;
			
			scanNext();
		}
		
		public int inputLength() {
			return buf.length-1;
		}
		
		public String asString() {
			return new String(sbuf, 0, sp);
		}
		
		public char peek() {
			return buf[bp+1];
		}
		
		private void convertUnicode() {
			if(ch == '\\' && unicodeConversionBp != bp) {
				ch = buf[++bp];
				
				if(ch == 'u') {
					do {
						ch = buf[++bp];
					} while(ch == 'u');
					
					int lim = bp + 3;
					if(lim < inputLength()) {
						int d = digit(16);
						int code = d;
						
						while(bp < lim && d >= 0) {
							ch = buf[++bp];
							d = digit(16);
							code = (code << 4) + d;
						}
						
						if(d >= 0) {
							ch = (char) code;
							unicodeConversionBp = bp;
							return;
						}
					}
					throw new IllegalStateException("illegal unicode escape");
				} else {
					bp--;
					ch = '\\';
				}
			}
		}
		
		public boolean isUnicode() {
			return unicodeConversionBp == bp;
		}
		
		public void scanNext() {
			if(bp < buf.length) {
				ch = buf[++bp];
				
				if(ch == '\\') {
					convertUnicode();
				}
			} else {
				throw new IllegalStateException("tried to scan outside of buf");
			}
		}
		
		public void skipChar() {
			bp++;
		}
		
		private int calcNewLen(int cur, int req) {
			if(cur < req + 1) {
				cur *= 2;
			}
			return cur;
		}
		
		private char[] ensureCapacity(char[] arr, int req) {
			if(req < arr.length) {
				return arr;
			} else {
				int newLen = calcNewLen(arr.length, req);
				char[] res = new char[newLen];
				System.arraycopy(arr, 0, res, 0, arr.length);
				return res;
			}
		}
		
		public void putChar(char ch) {
			putChar(ch, false);
		}
		
		public void putChar(boolean scan) {
			putChar(ch, scan);
		}
		
		public void putChar(char ch, boolean scan) {
			sbuf = ensureCapacity(sbuf, sp);
			sbuf[sp++] = ch;
			
			if(scan) {
				scanNext();
			}
		}
		
		private void skipIllegalUnderscores() {
			if(ch == '_') {
				throw new IllegalStateException();
			}
		}
		
		private int digit(int base) {
			char c = ch;
			int result = Character.digit(c, base);
			if(result >= 0 && c > 0x7f) {
				throw new IllegalStateException("non ascii digit: " + c);
			}
			return result;
		}
		
		private void scanDigits(int digitRadix) {
			char saveCh;
			
			do {
				if(ch != '_') {
					putChar(false);
				}
				
				saveCh = ch;
				scanNext();
			} while(digit(digitRadix) >= 0 || ch == '_');
			
			if(saveCh == '_') {
				throw new IllegalStateException("illegal underscore");
			}
		}
		
		private void scanNumber(int radix) {
			nradix = radix;
			
			// for octal, allow base-10 digit in case it's a float literal
			int digitRadix = (radix == 8 ? 10 : radix);
			
			boolean seendigit = false;
			if(digit(digitRadix) >= 0) {
				seendigit = true;
				scanDigits(digitRadix);
			}
			
			if(radix == 16 && ch == '.') {
				scanHexFractionAndSuffix(seendigit);
			} else if(seendigit && radix == 16 && (ch == 'p' || ch == 'P')) {
				scanHexExponentAndSuffix();
			} else if(digitRadix == 10 && ch == '.') {
				putChar(true);
				scanFractionAndSuffix();
			} else if(digitRadix == 10 &&
					(ch == 'e' || ch == 'E' || 
					 ch == 'f' || ch == 'F' ||
					 ch == 'd' || ch == 'D')) {
				scanFractionAndSuffix();
			} else {
				if(ch == 'l' || ch == 'L') {
					scanNext();
					tk = TokenKind.LONGLIT;
				} else {
					tk = TokenKind.INTLIT;
				}
			}
		}
		
		private void scanFraction() {
			skipIllegalUnderscores();
			if('0' <= ch && ch <= '9') {
				scanDigits(10);
			}
			
			if(ch == 'e' || ch == 'E') {
				putChar(true);
				skipIllegalUnderscores();
				
				if(ch == '+' || ch == '-') {
					putChar(true);
				}
				
				skipIllegalUnderscores();
				
				if('0' <= ch && ch <= '0') {
					scanDigits(10);
					return;
				}
				
				throw new IllegalStateException("malformed fp lit");
			}
			
		}
		
		private void scanFractionAndSuffix() {
			nradix = 10;
			scanFraction();
			
			if(ch == 'f' || ch == 'F') {
				putChar(true);
				tk = TokenKind.FLOATLIT;
			} else {
				if(ch == 'd' || ch == 'D') {
					putChar(true);
				}
				tk = TokenKind.DOUBLELIT;
			}
			
		}
		
		private void scanHexFractionAndSuffix(boolean seendigit) {
			nradix = 16;
			
			if(ch != '.') {
				throw new IllegalStateException();
			}
			
			putChar(true);
			skipIllegalUnderscores();
			
			if(digit(16) >= 0) {
				seendigit = true;
				scanDigits(16);
			}
			
			if(!seendigit) {
				throw new IllegalStateException("illegal hex number");
			} else {
				scanHexExponentAndSuffix();
			}
		}
		
		private void scanHexExponentAndSuffix() {
			if(ch == 'p' || ch == 'P') {
				putChar(true);
				skipIllegalUnderscores();
				
				if(ch == '+' || ch == '-') {
					putChar(true);
				}
				skipIllegalUnderscores();
				
				if('0' <= ch && ch <= '9') {
					scanDigits(10);
				}
			} else {
				throw new IllegalStateException("malformed fp input");
			}
			
			if(ch == 'f' || ch == 'F') {
				putChar(true);
				tk = TokenKind.FLOATLIT;
				nradix = 16;
			} else {
				if(ch == 'd' || ch == 'D') {
					putChar(true);
				}
				tk = TokenKind.DOUBLELIT;
				nradix = 16;
			}
		}
		
		private void scanLitChar() {
			if(ch == '\\') {
				if(peek() == '\\' && !isUnicode()) {
					skipChar();
					putChar('\\', true);
				} else {
					scanNext();
					
					switch(ch) {
						case '0': case '1': case '2': case '3':
						case '4': case '5': case '6': case '7':
							char leadch = ch;
							
							int oct = digit(8);
							scanNext();
							
							if('0' <= ch && ch <= '7') {
								oct = (oct * 8) + digit(8);
								scanNext();
								
								if(leadch <= '3' && '0' <= ch && ch <= '7') {
									oct = (oct * 8) + digit(8);
									scanNext();
								}
							}
							putChar((char) oct);
							break;
						case 'b':
							putChar('\b', true);
							break;
						case 't':
							putChar('\t', true);
							break;
						case 'n':
							putChar('\n', true);
							break;
						case 'f':
							putChar('\f', true);
							break;
						case 'r':
							putChar('\r', true);
							break;
						case '\'':
							putChar('\'', true);
							break;
						case '\"':
							putChar('\"', true);
							break;
						case '\\':
							putChar('\\', true);
							break;
						default:
							throw new IllegalStateException("illegal escape character: " + ch);
					}
				}
			} else if(bp != inputLength()) {
				putChar(true);
			}
		}
		
		public void process() {
			switch(ch) {
				case '0':
					scanNext();
					
					if(ch == 'x' || ch == 'X') {
						scanNext();
						skipIllegalUnderscores();
						
						if(ch == '.') {
							scanHexFractionAndSuffix(false);
						} else if(digit(16) < 0) {
							throw new IllegalArgumentException("invalid hex number");
						} else {
							scanNumber(16);
						}
						
					} else if(ch == 'b' || ch == 'B') {
						scanNext();
						skipIllegalUnderscores();
						if(digit(2) < 0) {
							throw new IllegalStateException("invalid binary number");
						} else {
							scanNumber(2);
						}
					} else {
						putChar('0');
						if(ch == '_') {
							do {
								scanNext();
							} while(ch == '_');
							
							if(digit(10) < 0) {
								throw new IllegalStateException("illegal underscore");
							}
						}
						scanNumber(8);
					}
					break;
					
				case '1': case '2': case '3': case '4':
				case '5': case '6': case '7': case '8': case '9':
					scanNumber(10);
					break;
				case '.':
					scanNext();
					if('0' <= ch && ch <= '9') {
						putChar('.');
						scanFractionAndSuffix();
					} else {
						throw new IllegalStateException("illegal dot (expected fp)");
					}
					break;
					
				case '\'':
					scanNext();
					if(ch == '\'') {
						throw new IllegalStateException("empty character literal");
					} else {
						if(ch == '\n' || ch == '\r') {
							throw new IllegalStateException("illegal line ending in character literal");
						}
						
						scanLitChar();
						if(ch == '\'') {
							scanNext();
							tk = TokenKind.CHARLIT;
						} else {
							throw new IllegalStateException("unclosed char literal");
						}
					}
					break;
				case '\"':
					scanNext();
					while(ch != '\"' && ch != '\r' && ch != '\r' && bp < inputLength()) {
						scanLitChar();
					}
					
					if(ch == '\"') {
						tk = TokenKind.STRLIT;
						scanNext();
					} else {
						throw new IllegalStateException("unclosed string literal");
					}
					break;
				default:
					throw new IllegalStateException("unexpected char: " + ch);
			}
		}
		
		public static int asInt(String s, int radix) throws NumberFormatException {
			if (radix == 10) {
				return Integer.parseInt(s, radix);
			} else {
				char[] cs = s.toCharArray();
				int limit = Integer.MAX_VALUE / (radix / 2);
				int n = 0;
				for (int i = 0; i < cs.length; i++) {
					int d = Character.digit(cs[i], radix);
					if (n < 0 || n > limit || n * radix > Integer.MAX_VALUE - d)
						throw new NumberFormatException();
					n = n * radix + d;
				}
				return n;
			}
		}
		
		public static long asLong(String s, int radix) throws NumberFormatException {
			if (radix == 10) {
				return Long.parseLong(s, radix);
			} else {
				char[] cs = s.toCharArray();
				long limit = Long.MAX_VALUE / (radix / 2);
				long n = 0;
				for (int i = 0; i < cs.length; i++) {
					int d = Character.digit(cs[i], radix);
					if (n < 0 || n > limit || n * radix > Long.MAX_VALUE - d)
						throw new NumberFormatException();
					n = n * radix + d;
				}
				return n;
			}
		}
	}
}
