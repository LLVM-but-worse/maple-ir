package testcases.condbranch;

import org.mapleir.deob.PassGroup;
import org.mapleir.deob.passes.ConstantParameterPass;
import org.mapleir.deob.passes.DeadCodeEliminationPass;
import org.mapleir.deob.passes.eval.ConstantExpressionEvaluatorPass;
import testcases.CheckReturn;
import testcases.FlaggedMethod;

public class ConditionalBranch1 {

	static class A {
		int m1(int x, int y) {
			if(x == 0) {
				return x;
			} else {
				return y;
			}
		}
	}
	
	static class A2 {
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
	
	static abstract class A3 {
		abstract int m3(int x, int y);
	}
	
	static class B3 extends A3 {
		@Override
		int m3(int x, int y) {
			if(x == 0) {
				return x;
			} else {
				return y;
			}
		}
	}
	
	static class D3 extends A3 {
		@Override
		int m3(int x, int y) {
			if(x == 0) {
				return x;
			} else {
				return y;
			}
		}
	}
	
	static class A4 {
		@FlaggedMethod
		int m4(int x, int y) {
			return x * 2 + y * 5;
		}
	}
	
	static class B4 extends A4 {
		@FlaggedMethod
		@Override
		int m4(int x, int y) {
			if(x == 0) {
				return x;
			} else {
				return y;
			}
		}
	}
	
	@CheckReturn
	static int test1() {
		A a = new A();
		return a.m1(1, 2);
	}
	
	@CheckReturn
	static int test2() {
		B2 b2 = new B2();
		return b2.m2(0, 1);
	}
	
	static B3 b3 = new B3();
	static D3 d3 = new D3();
	
	@CheckReturn
	static int test3() {
		A3 a = b3;
		return a.m3(0, 1);
	}
	
	@CheckReturn
	static int test4() {
		A3 a = d3;
		return a.m3(0, 2);
	}
	
	@CheckReturn
	static int test5() {
		D3 d = new D3();
		return d.m3(0, 1);
	}
	
	static B4 a4 = new B4();
	
	@CheckReturn
	static int test6() {
		A4 a = a4;
		return a.m4(0, 1);
	}
	
	@CheckReturn
	static int test7() {
		B4 b = a4;
		return b.m4(1, 5);
	}
	
	public static Class<?>[] getClasses() {
		return new Class<?>[] {
				ConditionalBranch1.class, 
				A.class, 
				A2.class, B2.class, 
				A3.class, B3.class, D3.class,
				A4.class, B4.class 
		};
	}
	
	public static PassGroup getPasses() {
		return new PassGroup(null)
				.add(new ConstantParameterPass())
				.add(new ConstantExpressionEvaluatorPass())
				.add(new DeadCodeEliminationPass());
	}
}