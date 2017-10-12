package org.mapleir.ir.antlr;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JFrame;
import javax.swing.JPanel;

import org.antlr.v4.gui.TreeViewer;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.mapleir.ir.antlr.mapleirParser.CompilationUnitContext;
import org.mapleir.ir.code.expr.PhiExpr;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.code.stmt.copy.CopyPhiStmt;
import org.mapleir.ir.locals.VersionedLocal;
import org.objectweb.asm.Type;

public class AntlrBoot {

	public static void main(String[] args) {
		
		VarExpr v = new VarExpr(new VersionedLocal(new AtomicInteger(0), 1, 0), Type.INT_TYPE);
		CopyPhiStmt copy = new CopyPhiStmt(v, new PhiExpr(new HashMap<>()));
		System.out.println(copy);
		
		try {
			InputStream testInputStream = AntlrBoot.class.getResourceAsStream("/sample.txt");
			ANTLRInputStream input = new ANTLRInputStream(testInputStream);

			mapleirLexer lexer = new mapleirLexer(input);
			CommonTokenStream tokens = new CommonTokenStream(lexer);
			mapleirParser parser = new mapleirParser(tokens);
			parser.addParseListener(new MapleIRCompiler());

			// Start parsing
//			parser.program();
//			System.out.println(parser.classType().getText());
			
			JFrame frame = new JFrame("Antlr AST");
	        JPanel panel = new JPanel();
	        CompilationUnitContext cu = parser.compilationUnit();
	        
	        for(Token t : tokens.getTokens()) {
//	        	System.out.println(t);
	        }
	        
	        TreeViewer viewr = new TreeViewer(Arrays.asList(
	                parser.getRuleNames()), cu);
	        viewr.setScale(1.3);//scale a little
	        panel.add(viewr);
	        frame.add(panel);
	        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	        frame.setSize(800, 600);
	        frame.setLocationRelativeTo(null);
	        frame.setVisible(true);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}