package org.mapleir.stdlib.util;

import java.io.StringWriter;

public class TabbedStringWriter {

	private static final String NEWLINE = System.lineSeparator();
	
	private StringWriter buff;
	private int tabCount;
	private String tabString;
	
	public TabbedStringWriter() {
		buff = new StringWriter();
		tabCount = 0;
		tabString = "   ";
	}

	public TabbedStringWriter print(CharSequence str) {
		for (int i = 0; i < str.length(); i++) {
			print(str.charAt(i));
		}
		return this;
	}
	
	public TabbedStringWriter print(char c, boolean indent) {
		buff.append(c);
		if (c == '\n' && indent) {
			buff.append(getTabs());
		}
		return this;
	}
	
	public TabbedStringWriter print(char c) {
		print(c, true);
		return this;
	}
	
	public TabbedStringWriter newline() {
		return print(NEWLINE);
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
	
	public TabbedStringWriter tab() {
		tabCount++;
		return this;
	}
	
	public TabbedStringWriter forceIndent() {
		buff.append(getTabs());
		return this;
	}

	public TabbedStringWriter untab() {
		if (tabCount <= 0) {
			throw new UnsupportedOperationException();
		}
		tabCount--;
		return this;
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