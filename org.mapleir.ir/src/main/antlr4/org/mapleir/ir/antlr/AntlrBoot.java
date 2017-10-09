package org.mapleir.ir.antlr;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;

public class AntlrBoot {

	public static void main(String[] args) {
		try {
			InputStream testInputStream = AntlrBoot.class.getResourceAsStream("/sample.txt");
			ANTLRInputStream input = new ANTLRInputStream(testInputStream);

			GYOOLexer lexer = new GYOOLexer(input);
			GYOOParser parser = new GYOOParser(new CommonTokenStream(lexer));
			parser.addParseListener(new MyListener());

			// Start parsing
			parser.program();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}