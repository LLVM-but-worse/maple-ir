package org.mapleir.ir.antlr.internallex;

import java.util.Arrays;

import org.mapleir.ir.antlr.error.ErrorReporter;

public class Lexer {

	private static final int FF = 0xC;
	private static final int LF = 0xA;
	private static final int CR = 0xD;
	private static final int EOI = 0x1A;
	
	private final ErrorReporter errorReporter;
    private final char[] buf;
    private int bp;
    private int buflen;
    private int eofPos;
    private int unicodeConversionBp;
    private char ch;

    private char[] sbuf;
    private int sp;

    private Token tk;
    private int nradix;
    
    private int tPos;
    private int tEndPos;
    private int prevTEndPos;
    private int errPos;
    
    private Name.Table names;
    private Name name;
    private Keywords keywords;
    
    public Lexer(ErrorReporter errorReporter, char[] buf) {
    	this.errorReporter = errorReporter;
        this.buf = Arrays.copyOf(buf, buf.length + 1);
        this.buf[this.buf.length - 1] = EOI;
        eofPos = buf.length;
        
        names = new Name.Table();
        keywords = new Keywords(names);
        
        unicodeConversionBp = bp = -1;
        ch = 0;
        buflen = buf.length;
        sbuf = new char[128];
        sp = 0;
        bp = -1;
        errPos = -1;
        
        scanNext();
    }

    public int getBufferPointer() {
        return bp;
    }

    public Token token() {
        return tk;
    }

    public int tokenPos() {
    	return tPos;
    }
    
    public int tokenEndPos() {
    	return tEndPos;
    }
    
    public int prevTokenEndPos() {
    	return prevTEndPos;
    }
    
    public int errPos() {
    	return errPos;
    }
    
    public Name name() {
    	return name;
    }
    
    public int radix() {
    	return nradix;
    }

    public String asString() {
        return new String(sbuf, 0, sp);
    }

    public char peek() {
        return buf[bp + 1];
    }

    private void convertUnicode() {
        if (ch == '\\' && unicodeConversionBp != bp) {
            ch = buf[++bp];

            if (ch == 'u') {
                do {
                    ch = buf[++bp];
                } while (ch == 'u');

                int lim = bp + 3;
                if (lim < buflen) {
                    int d = digit(16);
                    int code = d;

                    while (bp < lim && d >= 0) {
                        ch = buf[++bp];
                        d = digit(16);
                        code = (code << 4) + d;
                    }

                    if (d >= 0) {
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

    public void scanNext() {
        if (bp < buf.length) {
            ch = buf[++bp];

            if (ch == '\\') {
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
        if (cur < req + 1) {
            cur *= 2;
        }
        return cur;
    }

    private char[] ensureCapacity(char[] arr, int req) {
        if (req < arr.length) {
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

        if (scan) {
            scanNext();
        }
    }

    private void skipIllegalUnderscores() {
        if (ch == '_') {
            error("illegal underscore");
        }
    }

    private int digit(int base) {
        char c = ch;
        int result = Character.digit(c, base);
        if (result >= 0 && c > 0x7f) {
            error("non ascii digit: " + c);
        }
        return result;
    }

    private void scanDigits(int digitRadix) {
        char saveCh;

        do {
            if (ch != '_') {
                putChar(false);
            }

            saveCh = ch;
            scanNext();
        } while (digit(digitRadix) >= 0 || ch == '_');

        if (saveCh == '_') {
            error("illegal underscore");
        }
    }

    private void scanNumber(int radix) {
        nradix = radix;

        // for octal, allow base-10 digit in case it's a float literal
        int digitRadix = (radix == 8 ? 10 : radix);

        boolean seendigit = false;
        if (digit(digitRadix) >= 0) {
            seendigit = true;
            scanDigits(digitRadix);
        }

        if (radix == 16 && ch == '.') {
            scanHexFractionAndSuffix(seendigit);
        } else if (seendigit && radix == 16 && (ch == 'p' || ch == 'P')) {
            scanHexExponentAndSuffix();
        } else if (digitRadix == 10 && ch == '.') {
            putChar(true);
            scanFractionAndSuffix();
        } else if (digitRadix == 10 && (ch == 'e' || ch == 'E' || ch == 'f'
                || ch == 'F' || ch == 'd' || ch == 'D')) {
            scanFractionAndSuffix();
        } else {
            if (ch == 'l' || ch == 'L') {
                scanNext();
                tk = Token.LONGLIT;
            } else {
                tk = Token.INTLIT;
            }
        }
    }

    private void scanFraction() {
        skipIllegalUnderscores();
        if ('0' <= ch && ch <= '9') {
            scanDigits(10);
        }

        if (ch == 'e' || ch == 'E') {
            putChar(true);
            skipIllegalUnderscores();

            if (ch == '+' || ch == '-') {
                putChar(true);
            }

            skipIllegalUnderscores();

            if ('0' <= ch && ch <= '0') {
                scanDigits(10);
                return;
            }

            error("malformed fp lit");
        }
    }

    private void scanFractionAndSuffix() {
        nradix = 10;
        scanFraction();

        if (ch == 'f' || ch == 'F') {
            putChar(true);
            tk = Token.FLOATLIT;
        } else {
            if (ch == 'd' || ch == 'D') {
                putChar(true);
            }
            tk = Token.DOUBLELIT;
        }

    }

    private void scanHexFractionAndSuffix(boolean seendigit) {
        nradix = 16;

        if (ch != '.') {
            error("expected fractional part");
        }

        putChar(true);
        skipIllegalUnderscores();

        if (digit(16) >= 0) {
            seendigit = true;
            scanDigits(16);
        }

        if (!seendigit) {
            error("illegal hex number");
        } else {
            scanHexExponentAndSuffix();
        }
    }

    private void scanHexExponentAndSuffix() {
        if (ch == 'p' || ch == 'P') {
            putChar(true);
            skipIllegalUnderscores();

            if (ch == '+' || ch == '-') {
                putChar(true);
            }
            skipIllegalUnderscores();

            if ('0' <= ch && ch <= '9') {
                scanDigits(10);
            }
        } else {
            error("malformed fp input");
        }

        if (ch == 'f' || ch == 'F') {
            putChar(true);
            tk = Token.FLOATLIT;
            nradix = 16;
        } else {
            if (ch == 'd' || ch == 'D') {
                putChar(true);
            }
            tk = Token.DOUBLELIT;
            nradix = 16;
        }
    }

    private void scanLitChar() {
        if (ch == '\\') {
            if (peek() == '\\' && !isUnicode()) {
                skipChar();
                putChar('\\', true);
            } else {
                scanNext();

                switch (ch) {
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                        char leadch = ch;

                        int oct = digit(8);
                        scanNext();

                        if ('0' <= ch && ch <= '7') {
                            oct = (oct * 8) + digit(8);
                            scanNext();

                            if (leadch <= '3' && '0' <= ch && ch <= '7') {
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
        } else if (bp != buflen) {
            putChar(true);
        }
    }
    
	private char scanSurrogates() {
		if (Character.isHighSurrogate(ch)) {
			char high = ch;
			scanNext();
			if (Character.isLowSurrogate(ch)) {
				return high;
			}
			ch = high;
		}
		return 0;
	}

    private void scanIdent() {
        boolean isJavaIdentifierPart;
        char high;
        do {
            if (sp == sbuf.length) {
                putChar(ch); 
            } else {
                sbuf[sp++] = ch;
            }
            
            scanNext();
            switch (ch) {
                case 'A': case 'B': case 'C': case 'D': case 'E':
                case 'F': case 'G': case 'H': case 'I': case 'J':
                case 'K': case 'L': case 'M': case 'N': case 'O':
                case 'P': case 'Q': case 'R': case 'S': case 'T':
                case 'U': case 'V': case 'W': case 'X': case 'Y':
                case 'Z':
                case 'a': case 'b': case 'c': case 'd': case 'e':
                case 'f': case 'g': case 'h': case 'i': case 'j':
                case 'k': case 'l': case 'm': case 'n': case 'o':
                case 'p': case 'q': case 'r': case 's': case 't':
                case 'u': case 'v': case 'w': case 'x': case 'y':
                case 'z':
                case '$': case '_':
                case '0': case '1': case '2': case '3': case '4':
                case '5': case '6': case '7': case '8': case '9':
                case '\u0000': case '\u0001': case '\u0002': case '\u0003':
                case '\u0004': case '\u0005': case '\u0006': case '\u0007':
                case '\u0008': case '\u000E': case '\u000F': case '\u0010':
                case '\u0011': case '\u0012': case '\u0013': case '\u0014':
                case '\u0015': case '\u0016': case '\u0017':
                case '\u0018': case '\u0019': case '\u001B':
                case '\u007F':
                    break;
                case '\u001A': // EOI is also a legal identifier part
                    if (bp >= buflen) {
                        name = names.fromChars(sbuf, 0, sp);
                        tk = keywords.key(name);
                        return;
                    }
                    break;
                default:
                    if (ch < '\u0080') {
                        // all ASCII range chars already handled, above
                        isJavaIdentifierPart = false;
                    } else {
                        high = scanSurrogates();
                        if (high != 0) {
                            if (sp == sbuf.length) {
                                putChar(high);
                            } else {
                                sbuf[sp++] = high;
                            }
                            isJavaIdentifierPart = Character.isJavaIdentifierPart(
                                Character.toCodePoint(high, ch));
                        } else {
                            isJavaIdentifierPart = Character.isJavaIdentifierPart(ch);
                        }
                    }
                    if (!isJavaIdentifierPart) {
                        name = names.fromChars(sbuf, 0, sp);
                        tk = keywords.key(name);
                        return;
                    }
            }
        } while (true);
    }
    
    private void scanOperator() {
        while (true) {
            putChar(ch);
            Name newname = names.fromChars(sbuf, 0, sp);
            if (keywords.key(newname) == Token.IDENTIFIER) {
                sp--;
                break;
            }
            name = newname;
            tk = keywords.key(newname);
            scanNext();
            if (!isSpecial(ch))
            	break;
        }
    }
    
    private boolean isSpecial(char ch) {
        switch (ch) {
        	case '!': case '%': case '&': case '*': case '?':
        	case '+': case '-': case ':': case '<': case '=':
        	case '>': case '^': case '|': case '~':
        	case '@':
        		return true;
        	default:
        		return false;
        }
    }

	private void scanCommentChar() {
		scanNext();
		if (ch == '\\') {
			if (buf[bp + 1] == '\\' && unicodeConversionBp != bp) {
				bp++;
			} else {
				convertUnicode();
			}
		}
	}

    private void checkExtendedLiteral() {
    	if(ch == 'T' && peek() == '"') {
    		tk = Token.TYPELIT;
    		putChar('T');
    		scanNext();
    	}
    }
    
    public void nextToken() {
        try {
        	prevTEndPos = tEndPos;
            sp = 0;
            tk = null;
            scanToken0();
        } finally {
        	tEndPos = bp;
        }
    }
	
    private void scanToken0() {
    	while(true) {
            tPos = bp;
            checkExtendedLiteral();

        	System.out.printf("print: \"%c\"\n", ch);
            switch (ch) {
                case ' ':
                case '\t':
                case FF:
                    do {
                        scanNext();
                    } while(ch == ' ' || ch == '\t' || ch == FF);
                    tEndPos = bp;
                    break;
                case LF:
                    scanNext();
                    tEndPos = bp;
                    tk = Token.NEWLINE;
                    break;
                case CR:
                    scanNext();
                    if(ch == LF) {
                        scanNext();
                    }
                    tEndPos = bp;
                    tk = Token.NEWLINE;
                    break;
                case 'A': case 'B': case 'C': case 'D': case 'E':
                case 'F': case 'G': case 'H': case 'I': case 'J':
                case 'K': case 'L': case 'M': case 'N': case 'O':
                case 'P': case 'Q': case 'R': case 'S': case 'T':
                case 'U': case 'V': case 'W': case 'X': case 'Y':
                case 'Z':
                case 'a': case 'b': case 'c': case 'd': case 'e':
                case 'f': case 'g': case 'h': case 'i': case 'j':
                case 'k': case 'l': case 'm': case 'n': case 'o':
                case 'p': case 'q': case 'r': case 's': case 't':
                case 'u': case 'v': case 'w': case 'x': case 'y':
                case 'z':
                case '$': case '_':
                    scanIdent();
                    return;
                case '0':
                    scanNext();

                    if (ch == 'x' || ch == 'X') {
                        scanNext();
                        skipIllegalUnderscores();

                        if (ch == '.') {
                            scanHexFractionAndSuffix(false);
                        } else if (digit(16) < 0) {
                            throw new IllegalArgumentException(
                                    "invalid hex number");
                        } else {
                            scanNumber(16);
                        }

                    } else if (ch == 'b' || ch == 'B') {
                        scanNext();
                        skipIllegalUnderscores();
                        if (digit(2) < 0) {
                            error("invalid binary number");
                        } else {
                            scanNumber(2);
                        }
                    } else {
                        putChar('0');
                        if (ch == '_') {
                            do {
                                scanNext();
                            } while (ch == '_');

                            if (digit(10) < 0) {
                                error("illegal underscore");
                            }
                        }
                        scanNumber(8);
                    }
                    return;

                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    scanNumber(10);
                    return;
                case '.':
                    scanNext();
                    if ('0' <= ch && ch <= '9') {
                        putChar('.');
                        scanFractionAndSuffix();
                    } else if(ch == '.') {
                    	putChar('.');
                    	putChar('.');
                    	scanNext();
                    	if(ch == '.') {
                    		scanNext();
                    		putChar('.');
                    		tk = Token.ELLIPSIS;
                    	} else {
                    		error("illegal dot (expected fp)");
                    	}
                    } else {
                        tk = Token.DOT;
                    }
                    return;
    			case ',':
    				scanNext();
    				tk = Token.COMMA;
    				return;
    			case ';':
    				scanNext();
    				tk = Token.SEMI;
    				return;
    			case '(':
    				scanNext();
    				tk = Token.LPAREN;
    				return;
    			case ')':
    				scanNext();
    				tk = Token.RPAREN;
    				return;
    			case '[':
    				scanNext();
    				tk = Token.LBRACKET;
    				return;
    			case ']':
    				scanNext();
    				tk = Token.RBRACKET;
    				return;
    			case '{':
    				scanNext();
    				tk = Token.LBRACE;
    				return;
    			case '}':
    				scanNext();
    				tk = Token.RBRACE;
    				return;
    			case '/':
    				scanNext();
    				if(ch == '/') {
    					do {
    						scanCommentChar();
    					} while(ch != CR && ch != LF && bp < buflen);
    					if(bp < buflen) {
    						tEndPos = bp;
    					}
    					break;
    				} else if(ch == '*') {
    					while(bp < buflen) {
    						if(ch == '*') {
    							scanNext();
    							if(ch == '/') {
    								break;
    							}
    						} else {
    							scanCommentChar();
    						}
    					}
    					if(ch == '/') {
    						scanNext();
    						tEndPos = bp;
    						break;
    					} else {
    						error("unclosed comment");
    						return;
    					}
    				} else {
    					name = names.fromString("/");
    					tk = Token.SLASH;
    				}
    				return;
                case '\'':
                    scanNext();
                    if (ch == '\'') {
                        error("empty character literal");
                    } else {
                        if (ch == CR || ch == LF) {
                            error("illegal line ending in character literal");
                        }
                        scanLitChar();
                        if (ch == '\'') {
                            scanNext();
                            tk = Token.CHARLIT;
                        } else {
                            error("unclosed char literal");
                        }
                    }
                    return;
                case '\"':
                    scanNext();
                    while (ch != '\"' && ch != CR && ch != LF
                            && bp < buflen) {
                        scanLitChar();
                    }

                    if (ch == '\"') {
                        if (tk == null) {
                            // could be another kind of str lit
                            tk = Token.STRLIT;
                        }
                        scanNext();
                    } else {
                        error("unclosed string literal");
                    }
                    return;
                default:
                    if(isSpecial(ch)) {
                    	scanOperator();
                    } else {
                    	boolean isJavaIdentifierStart;
                    	if (ch < '\u0080') {
                            // all ASCII range chars already handled, above
                            isJavaIdentifierStart = false;
                    	} else {
                            char high = scanSurrogates();
                            if (high != 0) {
                                if (sp == sbuf.length) {
                                    putChar(high);
                                } else {
                                    sbuf[sp++] = high;
                                }

                                isJavaIdentifierStart = Character.isJavaIdentifierStart(
                                    Character.toCodePoint(high, ch));
                            } else {
                                isJavaIdentifierStart = Character.isJavaIdentifierStart(ch);
                            }
                        }
                        if (isJavaIdentifierStart) {
                            scanIdent();
                        } else if (bp == buflen || ch == EOI && bp+1 == buflen) {
                            tk = Token.EOF;
                            tPos = bp = eofPos;
                        } else {
                        	error(String.format("illegal char: %s", String.valueOf((int) ch)));
                            scanNext();
                        }
                    }
                    return;
            }
    	}
    }

    private void error(String msg) {
        System.out.println(msg);
        tk = Token.ERROR;
        errPos = tPos;
    }
}