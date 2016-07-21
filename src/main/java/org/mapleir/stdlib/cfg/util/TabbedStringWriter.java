package org.mapleir.stdlib.cfg.util;

import java.io.StringWriter;

public class TabbedStringWriter {

	private StringWriter buff;
	private int tabCount;

	public TabbedStringWriter() {
		buff = new StringWriter();
		tabCount = 0;
	}

	public void print(CharSequence str) {
		for (int i = 0; i < str.length(); i++) {
			print(str.charAt(i));
		}
	}

	public void print(char c, boolean indent) {
		buff.append(c);
		if (c == '\n' && indent) {
			buff.append(getTabs());
		}
	}
	
	public void print(char c) {
		print(c, true);
	}

	private String getTabs() {
		StringBuilder tabs = new StringBuilder();
		for (int i = 0; i < tabCount; i++) {
			tabs.append("   ");
		}
		return tabs.toString();
	}

	public int getTabCount() {
		return tabCount;
	}
	
	public void tab() {
		tabCount++;
	}

	public void untab() {
		if (tabCount <= 0) {
			throw new UnsupportedOperationException();
		}
		tabCount--;
	}

	@Override
	public String toString() {
		buff.flush();
		return buff.toString();
	}
}