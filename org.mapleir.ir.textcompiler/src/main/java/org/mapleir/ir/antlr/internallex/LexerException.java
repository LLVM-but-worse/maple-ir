package org.mapleir.ir.antlr.internallex;

public class LexerException extends Exception {
	private static final long serialVersionUID = -3663014223089886646L;
	
	private final int bufferPointer;
	
	public LexerException(String msg, int bufferPointer) {
		super(msg);
		this.bufferPointer = bufferPointer;
	}
	
	public int getBufferPointer() {
		return bufferPointer;
	}
}