package org.mapleir;

import java.util.Arrays;

public class CGExample3 {
	
	static int call1() {
		return 1;
	}	
	static int call2() {
		return 2;	
	}
	static int call3() {
		return 3;
	}
	static int call4() {
		return 4;
	}
	static boolean b() {
		return false;
	}
	
	static int x =0, y= 0, z = 0;
	
	public static void main(String[] args) {
		x = 0;
		y = 1;
		
		z = call1();
		
		x = z;
		y++;
		
		if(b()) {
			y += 5;
			z += call2();
			x = x + y;
		} else {
			x += 5;
			z -= call3();
			y = x + y;
		}
		
		y += x;
		x = z - x;
		
		z += call4();
		
		System.out.printf("x:%d, y:%d, z:%d%n", x, y, z);
		
		Arrays.copyOf(arr, 5)[x] = arr[y].toLowerCase();
//		if(arr[x].toLowerCase().matches(arr[y].toLowerCase())) {
//			System.out.println("ye");
//		} else {
//			System.out.println("ne");
//		}
	}
	
	static String[] arr = new String[0];
}