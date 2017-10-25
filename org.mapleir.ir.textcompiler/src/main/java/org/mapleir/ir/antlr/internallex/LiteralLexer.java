package org.mapleir.ir.antlr.internallex;

import java.util.Arrays;

public class LiteralLexer {
	
	private final char[] buf;
	private int bp;
	private int unicodeConversionBp;
	private char ch;
	
	private TokenKind tk;
	private int nradix;
	
	private char[] sbuf;
	private int sp;
	
	public LiteralLexer(char[] buf) {
		this.buf = Arrays.copyOf(buf, buf.length+1);
		this.unicodeConversionBp = this.bp = -1;
		this.ch = 0;
		
		buf[buf.length-1] = 0x10; // EOI
		
		sbuf = new char[128];
		sp = 0;
	}
	
	public int getBufferPointer() {
		return bp;
	}
	
	public TokenKind getTokenKind() {
		return tk;
	}
	
	public int getRadix() {
		return nradix;
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
	
	private void convertUnicode() throws LexerException {
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
				error("illegal unicode escape");
			} else {
				bp--;
				ch = '\\';
			}
		}
	}
	
	public boolean isUnicode() {
		return unicodeConversionBp == bp;
	}
	
	public void scanNext() throws LexerException {
		if(bp < buf.length) {
			ch = buf[++bp];
			
			if(ch == '\\') {
				convertUnicode();
			}
		} else {
			error("tried to scan outside of buf");
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
	
	public void putChar(char ch) throws LexerException {
		putChar(ch, false);
	}
	
	public void putChar(boolean scan) throws LexerException {
		putChar(ch, scan);
	}
	
	public void putChar(char ch, boolean scan) throws LexerException {
		sbuf = ensureCapacity(sbuf, sp);
		sbuf[sp++] = ch;
		
		if(scan) {
			scanNext();
		}
	}
	
	private void skipIllegalUnderscores() throws LexerException {
		if(ch == '_') {
			error("illegal underscore");
		}
	}
	
	private int digit(int base) throws LexerException {
		char c = ch;
		int result = Character.digit(c, base);
		if(result >= 0 && c > 0x7f) {
			error("non ascii digit: " + c);
		}
		return result;
	}
	
	private void scanDigits(int digitRadix) throws LexerException {
		char saveCh;
		
		do {
			if(ch != '_') {
				putChar(false);
			}
			
			saveCh = ch;
			scanNext();
		} while(digit(digitRadix) >= 0 || ch == '_');
		
		if(saveCh == '_') {
			error("illegal underscore");
		}
	}
	
	private void scanNumber(int radix) throws LexerException {
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
	
	private void scanFraction() throws LexerException {
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
			
			error("malformed fp lit");
		}
	}
	
	private void scanFractionAndSuffix() throws LexerException {
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
	
	private void scanHexFractionAndSuffix(boolean seendigit) throws LexerException {
		nradix = 16;
		
		if(ch != '.') {
			error("expected fractional part");
		}
		
		putChar(true);
		skipIllegalUnderscores();
		
		if(digit(16) >= 0) {
			seendigit = true;
			scanDigits(16);
		}
		
		if(!seendigit) {
			error("illegal hex number");
		} else {
			scanHexExponentAndSuffix();
		}
	}
	
	private void scanHexExponentAndSuffix() throws LexerException {
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
			error("malformed fp input");
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
	
	private void scanLitChar() throws LexerException {
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
						error("illegal escape character: " + ch);
				}
			}
		} else if(bp != inputLength()) {
			putChar(true);
		}
	}
	
	public void process() throws LexerException {
		scanNext();
		
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
						error("invalid binary number");
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
							error("illegal underscore");
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
					error("illegal dot (expected fp)");
				}
				break;
				
			case '\'':
				scanNext();
				if(ch == '\'') {
					error("empty character literal");
				} else {
					if(ch == '\n' || ch == '\r') {
						error("illegal line ending in character literal");
					}
					
					scanLitChar();
					if(ch == '\'') {
						scanNext();
						tk = TokenKind.CHARLIT;
					} else {
						error("unclosed char literal");
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
					error("unclosed string literal");
				}
				break;
			default:
				error("unexpected char: " + ch);
		}
	}
	
	private void error(String msg) throws LexerException {
		throw new LexerException(msg, bp);
	}
}