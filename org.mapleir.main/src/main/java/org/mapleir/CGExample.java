package org.mapleir;

public class CGExample {

	public static int fib0(int n) {
		return n;
	}
	
	public static int fib2(int n) {
		return fib(n-1) + fib(n-2);
	}
	
	public static int fib(int n) {
		if(n <= 1) {
			return fib0(n);
		} else {
			return fib2(n);
		}
	}
	
	public static void main(String[] args) {
		int f = fib(10);
		System.out.println(f);
	}
}