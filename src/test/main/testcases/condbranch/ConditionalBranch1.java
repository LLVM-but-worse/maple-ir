package testcases.condbranch;

import org.mapleir.deobimpl2.ConstantExpressionEvaluatorPass;
import org.mapleir.deobimpl2.ConstantParameterPass2;
import org.mapleir.deobimpl2.DeadCodeEliminationPass;
import org.mapleir.stdlib.deob.PassGroup;

import testcases.CheckReturn;

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
	
	public static Class<?>[] getClasses() {
		return new Class<?>[] {ConditionalBranch1.class, A.class, A2.class, B2.class, A3.class, B3.class, D3.class};
	}
	
	public static PassGroup getPasses() {
		return new PassGroup(null)
				.add(new ConstantParameterPass2())
				.add(new ConstantExpressionEvaluatorPass())
				.add(new DeadCodeEliminationPass());
	}
}