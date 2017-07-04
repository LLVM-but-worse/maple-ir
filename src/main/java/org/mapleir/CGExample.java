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
	
	void entry2() {
		head2();
	}
	
	void head2() {
		body2();
	}
	
	void body2() {
		exit2();
	}
	
	void exit2() {
		if(b) {
			head2();
		}
	}
	
	void exit() {
		if(b) {
			head1();
		} else {
			entry2();
		}
	}
	
	public static void main(String[] args) {
		new CGExample().entry();
	}
}