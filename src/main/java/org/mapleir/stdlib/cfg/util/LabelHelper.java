package org.mapleir.stdlib.cfg.util;

import org.mapleir.stdlib.cfg.BasicBlock;

/**
 * @author Bibl (don't ban me pls)
 * @created 24 May 2015
 */
public class LabelHelper {

	public static String createBlockName(long n) {
		char[] buf = new char[(int) Math.floor(Math.log(25 * (n + 1)) / Math.log(26))];
		for (int i = buf.length - 1; i >= 0; i--) {
			buf[i] = (char) ('A' + (--n) % 26);
			n /= 26;
		}
		return new String(buf);
	}
	
	public static String createBlockName(int n) {
		char[] buf = new char[(int) Math.floor(Math.log(25 * (n + 1)) / Math.log(26))];
		for (int i = buf.length - 1; i >= 0; i--) {
			buf[i] = (char) ('A' + (--n) % 26);
			n /= 26;
		}
		return new String(buf);
	}
	
	public static int numeric(String label) {
		int result = 0;
		for (int i = label.length() - 1; i >= 0; i--)
			result = result + (label.charAt(i) - 64) * (int) Math.pow(26, label.length() - (i + 1));
		return result;
	}
	
	public static int arrayIndex(BasicBlock b) {
		return numeric(b.getId()) - 1;
	}
}