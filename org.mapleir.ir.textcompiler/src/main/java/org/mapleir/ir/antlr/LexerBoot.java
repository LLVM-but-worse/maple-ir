package org.mapleir.ir.antlr;

import org.mapleir.ir.antlr.internallex.Lexer;
import org.mapleir.ir.antlr.internallex.LexerException;
import org.mapleir.ir.antlr.internallex.Token;

public class LexerBoot {

	public static void main(String[] args) throws LexerException {
		String input = "hi am the (((HEAD))) [nigger] number 5.283472384 in charge {};\n"
				+ "and \"i'm\" of type T\"negro\"@@@@@";
		Lexer lexer = new Lexer(null, input.toCharArray());
		
		do {
			lexer.nextToken();
			System.out.println(lexer.token());
		} while(lexer.token() != Token.EOF);
	}
}
