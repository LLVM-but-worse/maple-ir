package org.mapleir.ir.antlr.util;

public class Conversion {

	public static int asInt(String s, int radix) throws NumberFormatException {
		if (radix == 10) {
			return Integer.parseInt(s, radix);
		} else {
			char[] cs = s.toCharArray();
			int limit = Integer.MAX_VALUE / (radix / 2);
			int n = 0;
			for (int i = 0; i < cs.length; i++) {
				int d = Character.digit(cs[i], radix);
				if (n < 0 || n > limit || n * radix > Integer.MAX_VALUE - d)
					throw new NumberFormatException();
				n = n * radix + d;
			}
			return n;
		}
	}
	
	public static long asLong(String s, int radix) throws NumberFormatException {
		if (radix == 10) {
			return Long.parseLong(s, radix);
		} else {
			char[] cs = s.toCharArray();
			long limit = Long.MAX_VALUE / (radix / 2);
			long n = 0;
			for (int i = 0; i < cs.length; i++) {
				int d = Character.digit(cs[i], radix);
				if (n < 0 || n > limit || n * radix > Long.MAX_VALUE - d)
					throw new NumberFormatException();
				n = n * radix + d;
			}
			return n;
		}
	}
	
	public static boolean isZero(String s) {
		char[] cs = s.toCharArray();
		int base = ((cs.length > 1 && Character.toLowerCase(cs[1]) == 'x') ? 16 : 10);
		int i = ((base == 16) ? 2 : 0);
		while (i < cs.length && (cs[i] == '0' || cs[i] == '.')) {
			i++;
		}
		return !(i < cs.length && (Character.digit(cs[i], base) > 0));
	}
}