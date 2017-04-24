package org.mapleir;

public class CGExample {

	boolean b = false;
	
	public void entry() {
		head1();
	}
	
	void head1() {
		body1();
	}
	
	void body1() {
		exit();
	}
	
	void body2() {
	}
	
	void exit() {
		if(b) {
			head1();
		} else {
			body2();
		}
	}
}