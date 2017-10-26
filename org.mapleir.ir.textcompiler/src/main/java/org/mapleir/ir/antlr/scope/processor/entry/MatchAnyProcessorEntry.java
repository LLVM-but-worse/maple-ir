package org.mapleir.ir.antlr.scope.processor.entry;

import java.util.List;

import org.mapleir.ir.antlr.directive.DirectiveToken;

public class MatchAnyProcessorEntry extends AggregrateProcessorEntry {

	private boolean finishFast;
	
	public MatchAnyProcessorEntry(List<ProcessorEntry> entries) {
		this(entries, false);
	}
	
	public MatchAnyProcessorEntry(List<ProcessorEntry> entries, boolean finishFast) {
		super(entries);
		this.finishFast = finishFast;
	}
	
	public MatchAnyProcessorEntry(ProcessorEntry... entries) {
		this(false, entries);
	}
	
	public MatchAnyProcessorEntry(boolean finishFast, ProcessorEntry... entries) {
		super(entries);
		this.finishFast = finishFast;
	}

	@Override
	public boolean handle(DirectiveToken token) {
		boolean matched = false;
		
		for(ProcessorEntry entry : entries) {
			matched |= entry.handle(token);
			
			if(matched && finishFast) { 
				return true;
			}
		}
		
		return matched;
	}
}