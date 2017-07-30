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
	
	CGExample head2() {
		return body2();
	}
	
	CGExample body2() {
		return exit2();
	}
	
	CGExample exit2() {
		if(b) {
			return head2();
		}
		return null;
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
