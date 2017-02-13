//package org.mapleir.test;
//
//import static org.junit.Assert.*;
//
//import org.junit.Test;
//import org.mapleir.ir.cfg.ControlFlowGraph;
//import org.mapleir.ir.code.CodeUnit;
//import org.mapleir.ir.code.Expr;
//import org.mapleir.stdlib.util.TabbedStringWriter;
//import org.objectweb.asm.MethodVisitor;
//import org.objectweb.asm.Type;
//
//public class StatementTest {
//
//	static TestExpr stmt() {
//		return new TestExpr();
//	}
//
//	static void popul8(TestExpr s, int j) {
//		popul8(s, 0, j);
//	}
//
//	static void popul8(TestExpr s, int b, int j) {
//		for (int i = 0; i < j; i++) {
//			s.overwrite(stmt(), b + i);
//		}
//	}
//
//	@Test
//	public void testDeleteAt() {
//		// mathematically bad rigour test.
//		for (int i = 1; i < 16; i++) {
//			// random statement deletion
//			for (int j = 0; j < i; j++) {
////				Statement.ID_COUNTER = 0;
//				try {
//					TestExpr stmt = new TestExpr();
//					popul8(stmt, 0, i);
//					stmt.checkConsistency();
//					assertTrue(stmt.size() == i);
//					stmt.deleteAt(j);
//					stmt.checkConsistency();
//					assertTrue(stmt.size() == (i - 1));
//				} catch (AssertionError e) {
//					throw e;
//				} catch (RuntimeException e) {
//					throw new RuntimeException(i + ", " + j, e);
//				}
//			}
//
//			{
////				Statement.ID_COUNTER = 0;
//				TestExpr stmt = new TestExpr();
//				popul8(stmt, 0, i);
//				// forward deletion
//				for (int j = 0; j < i; j++) {
//					try {
//						stmt.checkConsistency();
//						assertEquals(String.format("i:%d, j:%d", i, j), stmt.size(), (i - j));
//						stmt.deleteAt(0);
//						stmt.checkConsistency();
//						assertEquals(String.format("i:%d, j:%d", i, j), stmt.size(), (i - j - 1));
//					} catch (AssertionError e) {
//						throw e;
//					} catch (RuntimeException e) {
//						throw new RuntimeException(i + ", " + j, e);
//					}
//				}
//			}
//
//			{
////				Statement.ID_COUNTER = 0;
//				TestExpr stmt = new TestExpr();
//				popul8(stmt, 0, i);
//				// forward deletion
//				int k = 0;
//				for (int j = i - 1; j >= 0; j--) {
//					try {
//						stmt.checkConsistency();
//						assertEquals(String.format("i:%d, j:%d", i, j), stmt.size(), (i - k));
//						stmt.deleteAt(j);
//						stmt.checkConsistency();
//						assertEquals(String.format("i:%d, j:%d", i, j), stmt.size(), (i - k - 1));
//						k++;
//					} catch (AssertionError e) {
//						throw e;
//					} catch (RuntimeException e) {
//						throw new RuntimeException(i + ", " + j, e);
//					}
//				}
//			}
//		}
//	}
//
//	@Test
//	public void testWriteAt() {
//		TestExpr stmt = stmt();
//
//	}
//
//	static class TestExpr extends Expr {
//
//		public TestExpr() {
//			super(0x1000);
//		}
//
//		@Override
//		public void onChildUpdated(int ptr) {
//		}
//
//		@Override
//		public void toString(TabbedStringWriter printer) {
//			printer.print("Stmt " + getId() + ". [");
//			printer.tab();
//
//			// print nulls if they aren't the trailing
//			// nulls in the array.
//
//			int end = 0; // exclusive
//			for (int i = children.length - 1; i >= 0; i--) {
//				if (children[i] != null) {
//					end = i + 1;
//					break;
//				}
//			}
//
//			for (int i = 0; i < end; i++) {
//				Expr c = children[i];
//				if (c != null) {
//					printer.print("\n");
//					c.toString(printer);
//				} else {
//					printer.print("\nNULL");
//				}
//			}
//
//			printer.untab();
//			if (end > 0) {
//				printer.print("\n");
//			}
//			printer.print("]");
//		}
//
//		@Override
//		public void toCode(MethodVisitor visitor, ControlFlowGraph cfg) {
//			throw new UnsupportedOperationException();
//		}
//
//		@Override
//		public boolean canChangeFlow() {
//			return false;
//		}
//
//		@Override
//		public boolean canChangeLogic() {
//			return false;
//		}
//
//		@Override
//		public boolean isAffectedBy(CodeUnit stmt) {
//			return false;
//		}
//
//		@Override
//		public Expr copy() {
//			TestExpr stmt = new TestExpr();
//			for (int i = 0; i < children.length; i++) {
//				stmt.overwrite(children[i].copy(), i);
//			}
//			return stmt;
//		}
//
//		@Override
//		public boolean equivalent(CodeUnit s) {
//			return false;
//		}
//
//		@Override
//		public Type getType() {
//			return null;
//		}
//	}
//}
