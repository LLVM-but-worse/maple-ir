package org.mapleir.ir.printer.indentation;

public class TabIndentationProvider implements IndentationProvider {
	@Override
	public String indent(String source, int indentationLevel) {
		if (indentationLevel <= 0)
			return source;

		String tabs = generateTabs(indentationLevel);

		return source.replaceAll("(?m)^", tabs);
	}

	private String generateTabs(int numTabs) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < numTabs; i++)
			sb.append('\t');
		return sb.toString();
	}
}
