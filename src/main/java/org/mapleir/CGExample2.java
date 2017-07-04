package org.mapleir;

public class CGExample2 {

	public boolean g = false;
	
	void big() {
		small();
		call();
		tail();
	}
	
	void small() {
		call();
		smalltail();
	}
	
	void call(){
		
	}
	
	public void smalltail() {
		if(g) {
			small();
		}
	}
	
	void tail() {
		if(g) {
			big();
		}
	}
	
	static boolean b() {
		return false;
	}
	
	public static void main(String[] args) {
		CGExample2 ex1 = new CGExample2();
		b();
		ex1.big();
	}
}