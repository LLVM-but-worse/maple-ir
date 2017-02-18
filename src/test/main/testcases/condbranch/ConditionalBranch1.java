package testcases.condbranch;

import org.mapleir.deobimpl2.ConstantExpressionEvaluatorPass;
import org.mapleir.deobimpl2.ConstantParameterPass2;
import org.mapleir.deobimpl2.DeadCodeEliminationPass;
import org.mapleir.stdlib.deob.PassGroup;

import testcases.Test;
import testcases.TestMethod;

public class ConditionalBranch1 {

	static class A {
		@TestMethod
		int m1(int x, int y) {
			if(x == 0) {
				return x;
			} else {
				return y;
			}
		}
	}
	
	static class B extends A {
//		int m1() {
//			return 5;
//		}
	}
	
	static class A2 {
		@TestMethod
		int m2(int x, int y) {
			if(x == 0) {
				return x;
			} else {
				return y;
			}
		}
	}
	
	static class B2 extends A2 {
		int m2() {
			return 5;
		}
	}
	
	static void test() {
		B b1 = new B();
		b1.m1(1, 2);
		
		B b2 = new B();
		b2.m1(1, 1);
	}
	
	public static String[] getClasses() {
		return Test.getNames(ConditionalBranch1.class, A.class, B.class);
	}
	
	public static PassGroup getPasses() {
		return new PassGroup(null)
				.add(new ConstantParameterPass2())
				.add(new ConstantExpressionEvaluatorPass())
				.add(new DeadCodeEliminationPass());
	}
}