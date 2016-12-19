package org.mapleir.ir;

public class t3 {

	static Object lock;
	
	public static void main(String[] args) {
		lock = "i am lock";
		
		t3 t = new t3();
		t.v_aq();
		t.v_rel();
	}
	
	void out1(Object o) {
		System.out.println(o);
	}
	
	void out2(Object o) {
		System.out.println(o);
	}
	
	void lock() {
		
	}
	
	void unlock() {
		
	}
	
	void v_aq() {
		System.out.println("l1");
		out1(lock);
		lock();
		out2(lock);
		System.out.println("l2");
	}
	
	void v_rel() {
		System.out.println("l3");
		out1(lock);
		unlock();
		out2(lock);
		System.out.println("l4");
	}
}