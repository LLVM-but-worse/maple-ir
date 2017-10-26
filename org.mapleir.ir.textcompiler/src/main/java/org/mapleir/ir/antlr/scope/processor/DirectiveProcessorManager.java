package org.mapleir.ir.antlr.scope.processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mapleir.ir.antlr.directive.DirectiveToken;
import org.mapleir.ir.antlr.scope.processor.entry.DefaultProcessorEntry;
import org.mapleir.ir.antlr.scope.processor.entry.ProcessorEntry;
import org.mapleir.ir.antlr.scope.processor.typematch.TypeMatcher;
import org.mapleir.ir.antlr.util.NullCheck;

public class DirectiveProcessorManager {

	private final Map<String, List<ProcessorEntry>> entries;
	
	public DirectiveProcessorManager() {
		entries = new HashMap<>();
	}
	
	public void handle(DirectiveToken token) {
		if(token != null) {
			String key = token.getKey();
			
			synchronized (entries) {
				if(entries.containsKey(key)) {
					List<ProcessorEntry> list = entries.get(key);
					
					for(ProcessorEntry e : list) {
						e.handle(token);
					}
				}
			}
		}
	}
	
	public void registerProcessorEntry(String key, ProcessorEntry entry) {
		synchronized (entries) {
			List<ProcessorEntry> list;
			
			if(entries.containsKey(key)) {
				list = entries.get(key);
			} else {
				list = new ArrayList<>();
				entries.put(key, list);
			}
			
			list.add(entry);
		}
	}
	
	public void registerProcessor(String key, TypeMatcher typeMatcher, DirectiveProcessor processor) {
		ProcessorEntry entry = makeProcessor(key, typeMatcher, processor);
		registerProcessorEntry(key, entry);
	}
	
	public ProcessorEntry makeProcessor(String key, TypeMatcher typeMatcher, DirectiveProcessor processor) {
		NullCheck.nonNull(key, "key");
		NullCheck.nonNull(processor, "processor");
		
		return new DefaultProcessorEntry(key, typeMatcher, processor);
	}
}