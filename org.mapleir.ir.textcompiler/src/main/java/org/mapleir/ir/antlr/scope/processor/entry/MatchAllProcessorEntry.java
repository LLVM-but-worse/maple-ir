package org.mapleir.ir.antlr.scope.processor.entry;

import java.util.List;

import org.mapleir.ir.antlr.directive.DirectiveToken;

public class MatchAllProcessorEntry extends AggregrateProcessorEntry {

	private final boolean failFast;

	public MatchAllProcessorEntry(List<ProcessorEntry> entries, boolean failFast) {
		super(entries);
		this.failFast = failFast;
	}
	
	public MatchAllProcessorEntry(boolean failFast, ProcessorEntry... entries) {
		super(entries);
		this.failFast = failFast;
	}

	@Override
	public boolean handle(DirectiveToken token) {
		boolean matchedAll = true;
		
		for(ProcessorEntry entry : entries) {
			matchedAll &= entry.handle(token);
			
			if(!matchedAll && failFast) {
				return false;
			}
		}
		
		return matchedAll;
	}
}