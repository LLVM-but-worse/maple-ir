package org.mapleir.ir.antlr.scope.processor.entry;

import java.util.ArrayList;
import java.util.List;

public abstract class AggregrateProcessorEntry implements ProcessorEntry {

	protected final List<ProcessorEntry> entries;
	
	public AggregrateProcessorEntry(List<ProcessorEntry> entries) {
		this.entries = entries;
	}

	public AggregrateProcessorEntry(ProcessorEntry... entries) {
		this.entries = new ArrayList<>();
		
		for(ProcessorEntry entry : entries) {
			this.entries.add(entry);
		}
	}
}