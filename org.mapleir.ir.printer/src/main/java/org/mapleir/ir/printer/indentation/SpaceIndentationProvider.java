package org.mapleir.ir.printer.indentation;

public class SpaceIndentationProvider implements IndentationProvider {
	private int numSpaces;
	
	public SpaceIndentationProvider(int numSpaces) {
		this.numSpaces = numSpaces;
	}
	
	@Override
	public String indent(String source, int indentationLevel) {
		if (indentationLevel <= 0)
			return source;

		String spaces = createSpaces(indentationLevel * numSpaces);

		return source.replaceAll("(?m)^", spaces);
	}
	
	private String createSpaces(int numSpaces) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < numSpaces; i++)
			sb.append('\t');
		return sb.toString();
	}
}
