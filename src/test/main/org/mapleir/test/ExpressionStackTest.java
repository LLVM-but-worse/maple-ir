package org.mapleir.test;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.mapleir.ir.code.Expr;
import org.mapleir.ir.code.ExpressionStack;
import org.mapleir.ir.code.expr.VarExpr;
import org.mapleir.ir.locals.BasicLocal;
import org.mapleir.ir.locals.Local;
import org.objectweb.asm.Type;

public class ExpressionStackTest {

	private static final Local TEST_LOCAL = new BasicLocal(new AtomicInteger(0), 1);
	@Test
	public void test() {
		ExpressionStack s = new ExpressionStack(1);
		assertEquals(0, s.size());
		
		s.push(makeExpr(1));
		
		assertEquals(1, s.size());		
		assertEquals(2, s.capacity());
		
		assertEquals(s.peek(), s.pop());
		
		assertEquals(0, s.size());
		assertEquals(2, s.capacity());
		
		for(int i=0; i < 3; i++) {
			s.push(makeExpr(2));
		}
		
		assertEquals(3, s.size());
		assertEquals(4, s.capacity());
		assertEquals(6, s.height());
		
		s.push(makeExpr(1));
		
		assertEquals(4, s.size());
		assertEquals(8, s.capacity());
		assertEquals(7, s.height());
		
		int i = 4;
		
		while(!s.isEmpty()) {
			assertEquals(i, s.size());
			s.pop();
			assertEquals(--i, s.size());
		}
		
		assertEquals(0, s.size());
		assertEquals(8, s.capacity());
		
		for(int j=0; j < s.capacity(); j++) {
			Expr e = s.getAt(j);
			if(e != null) {
				fail("floater");
			}
		}
		
		assertEquals(0, s.size());
		assertEquals(8, s.capacity());
	}
	
	private static Expr makeExpr(int size) {
		Type type = size == 1 ? Type.INT_TYPE : Type.DOUBLE_TYPE;
		return new VarExpr(TEST_LOCAL, type);
	}
}
