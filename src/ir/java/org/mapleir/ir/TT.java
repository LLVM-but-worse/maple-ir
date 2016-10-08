package org.mapleir.ir;

import org.mapleir.ir.code.Opcode;

public class TT {

	public static void main(String[] args) {
		
		System.out.println(Opcode.opname(Opcode.EPHI));
		
		int i1 = 0x301;
		int i2 = 0x101;

		System.out.println(Integer.toBinaryString(i1));
		System.out.println();
		System.out.println();
		
		for(int i=(32/8)-1; i >= 0; i--) {
			// look at each byte starting from 
			byte b = (byte) ((i1 >> (i * 8)) & 0xFF);
			System.out.println(String.format("%32s", Integer.toBinaryString(b)).replace(' ', '0'));
		}
		System.out.println();
		System.out.println();
		
		System.out.println(String.format("%32s", Integer.toBinaryString((i1 >> 24) & 0xFF)).replace(' ', '0'));
		System.out.println(String.format("%32s", Integer.toBinaryString((i1 >> 16) & 0xFF)).replace(' ', '0'));
		System.out.println(String.format("%32s", Integer.toBinaryString((i1 >> 8) & 0xFF)).replace(' ', '0'));
		System.out.println(String.format("%32s", Integer.toBinaryString((i1 >> 0) & 0xFF)).replace(' ', '0'));
		
		System.out.println();
		System.out.println();

		System.out.println(String.format("%32s", Integer.toBinaryString((i1 >> 8) & 0xFF)).replace(' ', '0'));
		System.out.println(String.format("%32s", Integer.toBinaryString((i1 >> 0) & 0xFF)).replace(' ', '0'));
		
		System.out.println();
		System.out.println();
		
		System.out.println(Integer.toBinaryString(i1));
		System.out.println(Integer.toBinaryString(i1 >> 8));
		System.out.println(Integer.toBinaryString((i1 >> 4) & 0xFF));
	}
}
