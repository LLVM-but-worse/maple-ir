package org.mapleir.ir.antlr.model;

import java.util.ArrayList;
import java.util.List;

import org.mapleir.ir.antlr.directive.DirectiveValue;
import org.mapleir.ir.cfg.ControlFlowGraph;

public class MethodDeclaration extends ClassMemberDeclaration {

	private ControlFlowGraph cfg;
	private List<HandlerTableEntry> handlerEntries;
	
	public MethodDeclaration() {
		handlerEntries = new ArrayList<>();
	}
	
	public List<HandlerTableEntry> getHandlerEntries() {
		return handlerEntries;
	}

	public void setHandlerEntries(List<HandlerTableEntry> handlerEntries) {
		this.handlerEntries = handlerEntries;
	}
	
	public ControlFlowGraph getCfg() {
		return cfg;
	}

	public void setCfg(ControlFlowGraph cfg) {
		this.cfg = cfg;
	}
	
	public static class HandlerTableEntry {
		public final DirectiveValue start, end, handler;

		public HandlerTableEntry(DirectiveValue start, DirectiveValue end, DirectiveValue handler) {
			this.start = start;
			this.end = end;
			this.handler = handler;
		}
	}
}