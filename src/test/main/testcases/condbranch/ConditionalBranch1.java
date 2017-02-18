package testcases.condbranch;

import org.mapleir.deobimpl2.ConstantExpressionEvaluatorPass;
import org.mapleir.deobimpl2.ConstantParameterPass2;
import org.mapleir.deobimpl2.DeadCodeEliminationPass;
import org.mapleir.stdlib.deob.PassGroup;

import testcases.CheckReturn;
import testcases.FlaggedMethod;

public class ConditionalBranch1 {

	static class A {
		@FlaggedMethod
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
		@FlaggedMethod
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
	
	@CheckReturn
	static int test1() {
		B b1 = new B();
		return b1.m1(1, 2);
	}
	
	@CheckReturn
	static int test2() {
		B b2 = new B();
		return b2.m1(1, 1);
	}
	
	public static Class<?>[] getClasses() {
		return new Class<?>[] {ConditionalBranch1.class, A.class, B.class};
	}
	
	public static PassGroup getPasses() {
		return new PassGroup(null)
				.add(new ConstantParameterPass2())
				.add(new ConstantExpressionEvaluatorPass())
				.add(new DeadCodeEliminationPass());
	}
}