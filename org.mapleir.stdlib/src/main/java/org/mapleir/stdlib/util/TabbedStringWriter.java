package org.mapleir.stdlib.util;

import java.io.StringWriter;

public class TabbedStringWriter {

	private StringWriter buff;
	private int tabCount;
	private String tabString;
	
	public TabbedStringWriter() {
		buff = new StringWriter();
		tabCount = 0;
		tabString = "   ";
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

	public void setTabString(String tabString) {
		this.tabString = tabString;
	}
	
	protected String getTabString() {
		return tabString;
	}
	
	private String getTabs() {
		StringBuilder tabs = new StringBuilder();
		for (int i = 0; i < tabCount; i++) {
			tabs.append(tabString);
		}
		return tabs.toString();
	}

	public int getTabCount() {
		return tabCount;
	}
	
	public void tab() {
		tabCount++;
	}
	
	public void forceIndent() {
		buff.append(getTabs());
	}

	public void untab() {
		if (tabCount <= 0) {
			throw new UnsupportedOperationException();
		}
		tabCount--;
	}
	
	public void clear() {
		buff = new StringWriter();
		tabCount = 0;
	}

	@Override
	public String toString() {
		buff.flush();
		return buff.toString();
	}
}