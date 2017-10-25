package org.mapleir.ir.antlr.util;

public class NullCheck {

	public static void nonNull(Object o, String itemName) {
		if(o == null) {
			throw new NullPointerException(itemName + " is null");
		}
	}
}