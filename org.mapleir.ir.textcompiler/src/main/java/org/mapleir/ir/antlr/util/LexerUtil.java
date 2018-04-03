package org.mapleir.ir.antlr.util;

import java.text.ParseException;

import org.mapleir.ir.antlr.internallex.LexerException;
import org.mapleir.ir.antlr.internallex.Lexer;
import org.objectweb.asm.Type;

public class LexerUtil {

	public static Object decodeNumericLiteral(String input) throws ParseException {
		Lexer lexer = new Lexer(input.toCharArray());
		
		try {
			lexer.nextToken();
		} catch(LexerException e) {
			throw new ParseException(e.getMessage(), e.getBufferPointer());
		}
		
		switch(lexer.getTokenKind()) {
			case INTLIT: {
				try {
					return Conversion.asInt(lexer.asString(), lexer.getRadix());
				} catch (NumberFormatException e) {
					throw new ParseException(e.getMessage(), lexer.getBufferPointer());
				}
			}
			case LONGLIT: {
				try {
					return Conversion.asLong(lexer.asString(), lexer.getRadix());
				} catch (NumberFormatException e) {
					throw new ParseException(e.getMessage(), lexer.getBufferPointer());
				}
			}
			case FLOATLIT: {
				String proper = lexer.getRadix() == 16 ? ("0x" + lexer.asString()) : lexer.asString();
				
				Float n;
				
				try {
					n = Float.valueOf(proper);
				} catch(NumberFormatException ex) {
					 // should throw before we get to here
					n = Float.NaN;
				}
				
				if(n.floatValue() == 0.0f && !Conversion.isZero(proper)) {
					throw new ParseException("fp number too small: " + proper, lexer.getBufferPointer());
				} else if(n.floatValue() == Float.POSITIVE_INFINITY) {
					throw new ParseException("fp number too large: " + proper, lexer.getBufferPointer());
				}
				
				return n;
			}
			case DOUBLELIT: {
				String proper = lexer.getRadix() == 16 ? ("0x" + lexer.asString()) : lexer.asString();
				
				Double n;
	            try {
	                n = Double.valueOf(proper);
	            } catch (NumberFormatException ex) {
	            	 // should throw before we get to here
	                n = Double.NaN;
	            }
	            
	            if(n.doubleValue() == 0.0d && !Conversion.isZero(proper)) {
					throw new ParseException("fp number too small: " + proper, lexer.getBufferPointer());
	            } else if(n.floatValue() == Float.POSITIVE_INFINITY) {
					throw new ParseException("fp number too large: " + proper, lexer.getBufferPointer());
				}
	            
	            return n;
			}
			case CHARLIT:
				return lexer.asString().charAt(0);
			case STRLIT:
				return lexer.asString();
			case TYPELIT:
			    String str = lexer.asString();
			    try {
			        return Type.getType(str);
			    } catch(IllegalArgumentException | UnsupportedOperationException e) {
			        throw new ParseException("illegal asm type: " + str, 0);
			    }
			default:
				throw new UnsupportedOperationException("Unknown token type: " + lexer.getTokenKind());
		}
	}
}